package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.inbound_service.client.WarehouseStockGateway;
import com.inbound_service.dto.request.CreateInboundReceiptRequest;
import com.inbound_service.dto.request.ReceiveLineRequest;
import com.inbound_service.dto.response.InboundReceiptResponse;
import com.inbound_service.entity.*;
import com.inbound_service.mapper.InboundReceiptMapper;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.InboundReceiptSpecification;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InboundReceiptService {

    private final InboundReceiptRepository receiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoItemRepository poItemRepository;
    private final PutawayTaskRepository putawayTaskRepository;
    private final WarehouseStockGateway warehouseStockGateway;
    private final InboundReceiptMapper receiptMapper;

    private static final EnumSet<PurchaseOrderStatus> RECEIVABLE_STATUSES =
            EnumSet.of(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.PARTIAL);

    /**
     * Tạo phiếu nhập kho: kiểm tra số lượng → tạo phiếu → cập nhật tồn kho → cập nhật PO.
     */
    @Transactional
    public InboundReceiptResponse createReceipt(CreateInboundReceiptRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu nhập phải có ít nhất một dòng nhận hàng");
        }

        // 1. Lấy PO và kiểm tra trạng thái
        PurchaseOrder po = purchaseOrderRepository.findById(request.purchaseOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));

        if (!RECEIVABLE_STATUSES.contains(po.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ nhận hàng cho đơn nhập đã duyệt (APPROVED) hoặc đang nhận dở (PARTIAL)");
        }

        // 2. Lấy danh sách dòng PO
        List<PoItem> poItems = poItemRepository.findByPurchaseOrderId(po.getId());
        Map<UUID, PoItem> poItemMap = new HashMap<>();
        for (PoItem item : poItems) {
            poItemMap.put(item.getId(), item);
        }

        // 3. Kiểm tra số lượng từng dòng
        List<InboundReceiptItem> receiptItems = new ArrayList<>();
        for (ReceiveLineRequest line : request.items()) {
            if (line.receivedQty() == null || line.receivedQty() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng nhận phải lớn hơn 0");
            }

            PoItem poItem = poItemMap.get(line.poItemId());
            if (poItem == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng PO " + line.poItemId() + " không thuộc đơn nhập này");
            }

            int currentReceived = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            int remaining = poItem.getOrderedQty() - currentReceived;

            if (line.receivedQty() > remaining) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "SKU " + poItem.getProductSku() + ": số lượng nhận (" + line.receivedQty()
                                + ") vượt quá số còn lại (" + remaining
                                + "), đã nhận " + currentReceived + "/" + poItem.getOrderedQty());
            }

            receiptItems.add(InboundReceiptItem.builder()
                    .poItem(poItem)
                    .productId(poItem.getProductId())
                    .productSku(poItem.getProductSku())
                    .receivedQty(line.receivedQty())
                    .note(line.note())
                    .build());
        }

        // 4. Tạo phiếu nhập kho
        InboundReceipt receipt = InboundReceipt.builder()
                .receiptNumber(generateUniqueReceiptNumber())
                .purchaseOrder(po)
                .warehouseId(po.getWarehouseId())
                .locationId(request.locationId())
                .status(InboundReceiptStatus.RECEIVED)
                .note(request.note())
                .receivedDate(LocalDate.now())
                .build();

        for (InboundReceiptItem item : receiptItems) {
            item.setReceipt(receipt);
        }
        receipt.setItems(receiptItems);
        receiptRepository.save(receipt);

        // 5. Cập nhật số lượng đã nhận trên dòng PO
        for (InboundReceiptItem receiptItem : receiptItems) {
            PoItem poItem = receiptItem.getPoItem();
            int current = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            poItem.setReceivedQty(current + receiptItem.getReceivedQty());
            poItemRepository.save(poItem);
        }

        // 6. Cập nhật tồn kho (gọi warehouse-service)
        for (InboundReceiptItem receiptItem : receiptItems) {
            StockAdjustCommand cmd = new StockAdjustCommand(
                    po.getWarehouseId(),
                    request.locationId(),
                    receiptItem.getProductId(),
                    null,
                    receiptItem.getReceivedQty());
            warehouseStockGateway.adjustOrThrow(cmd);
        }

        // 7. Cập nhật trạng thái PO (PARTIAL / COMPLETED)
        refreshPoStatus(po.getId());

        // 8. Tạo putaway tasks cho hàng vừa nhận
        for (InboundReceiptItem receiptItem : receiptItems) {
            PutawayTask task = PutawayTask.builder()
                    .poItem(receiptItem.getPoItem())
                    .inboundReceipt(receipt)
                    .productId(receiptItem.getProductId())
                    .qtyToPutaway(receiptItem.getReceivedQty())
                    .suggestedLocationId(request.locationId())
                    .status(PutawayStatus.PENDING)
                    .build();
            putawayTaskRepository.save(task);
        }

        return receiptMapper.toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InboundReceiptResponse> findAll(Pageable pageable, String keyword,
            UUID purchaseOrderId, UUID warehouseId, InboundReceiptStatus status) {
        Specification<InboundReceipt> spec = InboundReceiptSpecification.hasKeyword(keyword)
                .and(InboundReceiptSpecification.hasPurchaseOrderId(purchaseOrderId))
                .and(InboundReceiptSpecification.hasWarehouseId(warehouseId))
                .and(InboundReceiptSpecification.hasStatus(status));
        Page<InboundReceipt> page = receiptRepository.findAll(spec, pageable);
        Page<InboundReceiptResponse> mapped = page.map(receiptMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse findById(UUID id) {
        InboundReceipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu nhập"));
        return receiptMapper.toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> findByPurchaseOrderId(UUID purchaseOrderId) {
        return receiptRepository.findByPurchaseOrderId(purchaseOrderId).stream()
                .map(receiptMapper::toResponse)
                .toList();
    }

    // ---- helpers ----

    private void refreshPoStatus(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
        List<PoItem> lines = poItemRepository.findByPurchaseOrderId(purchaseOrderId);

        boolean allReceived = lines.stream()
                .allMatch(l -> Objects.equals(l.getOrderedQty(), l.getReceivedQty()));

        if (allReceived) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
        } else {
            po.setStatus(PurchaseOrderStatus.PARTIAL);
        }
        purchaseOrderRepository.save(po);
    }

    private String generateUniqueReceiptNumber() {
        for (int i = 0; i < 10; i++) {
            String candidate = CodeGenerator.generate("GRN");
            if (!receiptRepository.existsByReceiptNumber(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Không thể sinh mã phiếu nhập duy nhất, vui lòng thử lại");
    }
}
