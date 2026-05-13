package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.product_service.entity.Supplier;
import com.product_service.repository.SupplierRepository;
import com.product_service.repository.ProductRepository;
import com.warehouse_service.service.StockLevelService;
import com.inbound_service.dto.request.CreateInboundReceiptRequest;
import com.inbound_service.dto.request.ReceiveLineRequest;
import com.inbound_service.dto.response.InboundPrintItemResponse;
import com.inbound_service.dto.response.InboundPrintResponse;
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
    private final StockLevelService stockLevelService;
    private final InboundReceiptMapper receiptMapper;
    private final AuditLogService auditLogService;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    private static final EnumSet<PurchaseOrderStatus> RECEIVABLE_STATUSES =
            EnumSet.of(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.PARTIAL);

        // Tạo phiếu nhập kho và cập nhật tồn kho, trạng thái PO, putaway task.
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

        // 3. Kiểm tra số lượng từng dòng, kể cả khi request chứa nhiều dòng cho cùng một poItem
        Map<UUID, Integer> requestedQtyByPoItem = new HashMap<>();
        for (ReceiveLineRequest line : request.items()) {
            if (line.receivedQty() == null || line.receivedQty() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng nhận phải lớn hơn 0");
            }

            requestedQtyByPoItem.merge(line.poItemId(), line.receivedQty(), Integer::sum);
        }

        for (Map.Entry<UUID, Integer> entry : requestedQtyByPoItem.entrySet()) {
            PoItem poItem = poItemMap.get(entry.getKey());
            if (poItem == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng PO " + entry.getKey() + " không thuộc đơn nhập này");
            }

            int currentReceived = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            int remaining = poItem.getOrderedQty() - currentReceived;
            int requestedQty = entry.getValue();

            if (requestedQty > remaining) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "SKU " + poItem.getProductSku() + ": tổng số lượng nhận (" + requestedQty
                                + ") vượt quá số còn lại (" + remaining
                                + "), đã nhận " + currentReceived + "/" + poItem.getOrderedQty());
            }
        }

        List<InboundReceiptItem> receiptItems = new ArrayList<>();
        for (ReceiveLineRequest line : request.items()) {
            PoItem poItem = poItemMap.get(line.poItemId());

            if (poItem == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng PO " + line.poItemId() + " không thuộc đơn nhập này");
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
        Set<PoItem> updatedPoItems = new LinkedHashSet<>();
        for (InboundReceiptItem receiptItem : receiptItems) {
            PoItem poItem = receiptItem.getPoItem();
            int current = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            poItem.setReceivedQty(current + receiptItem.getReceivedQty());
            updatedPoItems.add(poItem);
        }
        if (!updatedPoItems.isEmpty()) {
            poItemRepository.saveAll(updatedPoItems);
        }

        // 6. Cập nhật tồn kho qua warehouse module.
        for (InboundReceiptItem receiptItem : receiptItems) {
            StockAdjustCommand cmd = new StockAdjustCommand(
                    po.getWarehouseId(),
                    request.locationId(),
                    receiptItem.getProductId(),
                    null,
                    receiptItem.getReceivedQty(),
                    "INBOUND_RECEIPT_ITEM:" + receiptItem.getId() + ":ADJUST",
                    "INBOUND_RECEIPT",
                    receipt.getId());
            stockLevelService.adjust(cmd);
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

        InboundReceiptResponse response = receiptMapper.toResponse(receipt);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("receiptNumber", receipt.getReceiptNumber());
        metadata.put("purchaseOrderId", po.getId());
        metadata.put("poNumber", po.getPoNumber());
        metadata.put("warehouseId", po.getWarehouseId());
        metadata.put("locationId", request.locationId());
        metadata.put("lineCount", receiptItems.size());
        auditLogService.record("INBOUND_RECEIPT", "CREATE", "Tạo phiếu nhập kho",
                "INBOUND_RECEIPT", receipt.getId(), receipt.getReceiptNumber(), null, response,
                request.note(), metadata);
        return response;
    }

    // Lấy danh sách phiếu nhập có phân trang và bộ lọc.
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

    // Lấy chi tiết phiếu nhập theo id.
    @Transactional(readOnly = true)
    public InboundReceiptResponse findById(UUID id) {
        InboundReceipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu nhập"));
        return receiptMapper.toResponse(receipt);
    }

    // Lấy dữ liệu in phiếu nhập kho (Packing List/GRN) chuyên dụng
    @Transactional(readOnly = true)
    public InboundPrintResponse getPrintData(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu nhập"));

        PurchaseOrder po = receipt.getPurchaseOrder();

        String supplierName = "";
        String supplierAddress = "";
        String supplierPhone = "";

        try {
            Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
            if (supplier != null) {
                supplierName = supplier.getName();
                supplierAddress = supplier.getAddress();
                supplierPhone = supplier.getContactPhone();
            }
        } catch (Exception e) {
            // fallback
        }

        List<InboundPrintItemResponse> itemResponses = new ArrayList<>();
        short stt = 1;
        for (InboundReceiptItem item : receipt.getItems()) {
            String productName = item.getProductSku();
            String unit = "";
            try {
                var product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null) {
                    productName = product.getName();
                    unit = product.getBaseUnit();
                }
            } catch (Exception e) {
                // fallback
            }

            Integer orderedQty = item.getPoItem() != null ? item.getPoItem().getOrderedQty() : item.getReceivedQty();

            itemResponses.add(new InboundPrintItemResponse(
                    stt++,
                    item.getProductId(),
                    item.getProductSku(),
                    productName,
                    unit,
                    orderedQty,
                    item.getReceivedQty(),
                    item.getNote()
            ));
        }

        return new InboundPrintResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                po.getPoNumber(),
                receipt.getWarehouseId(),
                receipt.getLocationId(),
                receipt.getReceivedDate(),
                supplierName,
                supplierAddress,
                supplierPhone,
                receipt.getReceivedBy(),
                receipt.getNote(),
                itemResponses
        );
    }

    // Lấy danh sách phiếu nhập theo đơn nhập.
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> findByPurchaseOrderId(UUID purchaseOrderId) {
        return receiptRepository.findByPurchaseOrderId(purchaseOrderId).stream()
                .map(receiptMapper::toResponse)
                .toList();
    }

    // ---- helpers ----

    // Đồng bộ trạng thái đơn nhập theo tổng tiến độ nhận hàng.
    private void refreshPoStatus(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
        List<PoItem> lines = poItemRepository.findByPurchaseOrderId(purchaseOrderId);

        boolean allReceived = lines.stream()
                .allMatch(l -> Objects.equals(l.getOrderedQty(), l.getReceivedQty()));

        boolean anyReceived = lines.stream()
                .anyMatch(l -> l.getReceivedQty() != null && l.getReceivedQty() > 0);

        if (allReceived) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrderStatus.PARTIAL);
        } else {
            po.setStatus(PurchaseOrderStatus.APPROVED);
        }
        purchaseOrderRepository.save(po);
    }

    // Sinh mã phiếu nhập duy nhất.
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
