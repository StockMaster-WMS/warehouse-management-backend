package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.dto.response.PurchaseOrderDetailResponse;
import com.inbound_service.dto.response.InboundReceiptResponse;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.InboundReceiptMapper;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.mapper.PurchaseOrderMapper;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PurchaseOrderSpecification;
import com.inbound_service.repository.PutawayTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoItemRepository poItemRepository;
    private final PutawayTaskRepository putawayTaskRepository;
    private final InboundReceiptRepository receiptRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PoItemMapper poItemMapper;
    private final PutawayTaskMapper putawayTaskMapper;
    private final InboundReceiptMapper receiptMapper;

    // Lấy danh sách đơn nhập có phân trang và bộ lọc.
    public PagedResponse<PurchaseOrderResponse> findAll(Pageable pageable, String keyword, String status,
            UUID supplierId, UUID warehouseId) {
        Specification<PurchaseOrder> spec = PurchaseOrderSpecification.hasKeyword(keyword)
                .and(PurchaseOrderSpecification.hasStatus(status))
                .and(PurchaseOrderSpecification.hasSupplierId(supplierId))
                .and(PurchaseOrderSpecification.hasWarehouseId(warehouseId));
        Page<PurchaseOrder> page = purchaseOrderRepository.findAll(spec, pageable);
        Page<PurchaseOrderResponse> mapped = page.map(purchaseOrderMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết đơn nhập theo id.
    public PurchaseOrderResponse findById(UUID id) {
        return purchaseOrderMapper.toResponse(getPurchaseOrder(id));
    }

    // Lấy chi tiết đơn nhập theo mã PO.
    public PurchaseOrderResponse findByPoNumber(String poNumber) {
        return purchaseOrderMapper.toResponse(purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập")));
    }

    // Lấy đầy đủ thông tin đơn nhập gồm dòng hàng, putaway và phiếu nhập.
    public PurchaseOrderDetailResponse findDetail(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        List<PoItem> items = poItemRepository.findByPurchaseOrderId(id);
        List<PutawayTask> putawayTasks = putawayTaskRepository.findByPurchaseOrderIdWithPoItem(id);
        List<InboundReceipt> receipts = receiptRepository.findByPurchaseOrderId(id);

        List<PoItemResponse> itemResponses = items.stream()
            .map(poItemMapper::toResponse)
            .toList();

        List<PutawayTaskResponse> putawayResponses = putawayTasks.stream()
            .map(putawayTaskMapper::toResponse)
            .toList();

        List<InboundReceiptResponse> receiptResponses = receipts.stream()
            .map(receiptMapper::toResponse)
            .toList();

        int totalOrderedQty = items.stream()
            .mapToInt(item -> item.getOrderedQty() == null ? 0 : item.getOrderedQty())
            .sum();
        int totalReceivedQty = items.stream()
            .mapToInt(item -> item.getReceivedQty() == null ? 0 : item.getReceivedQty())
            .sum();
        boolean fullyReceived = !items.isEmpty() && totalOrderedQty == totalReceivedQty;

        return new PurchaseOrderDetailResponse(
            purchaseOrderMapper.toResponse(purchaseOrder),
            itemResponses,
            putawayResponses,
            receiptResponses,
            totalOrderedQty,
            totalReceivedQty,
            fullyReceived);
    }

    // Tạo mới đơn nhập và sinh mã PO duy nhất.
    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = purchaseOrderMapper.toEntity(request);
        purchaseOrder.setPoNumber(generateUniquePoNumber());

        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    // Cập nhật đơn nhập khi chưa kết thúc.
    @Transactional
    public PurchaseOrderResponse update(UUID id, UpdatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        requirePoNotFinished(purchaseOrder);

        purchaseOrderRepository.findByPoNumber(request.poNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn nhập đã tồn tại");
                });

        purchaseOrderMapper.updateEntity(request, purchaseOrder);

        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    // Xóa đơn nhập khi chưa có dòng hàng và chưa kết thúc.
    @Transactional
    public void delete(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        requirePoNotFinished(purchaseOrder);
        if (!poItemRepository.findByPurchaseOrderId(id).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể xóa PO đã có dòng hàng");
        }
        purchaseOrderRepository.delete(purchaseOrder);
    }

    public boolean existsBySupplierId(UUID supplierId) {
        return purchaseOrderRepository.existsBySupplierId(supplierId);
    }

    // Duyệt đơn nhập từ trạng thái DRAFT sang APPROVED.
    @Transactional
    public PurchaseOrderResponse approve(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ duyệt đơn nhập ở trạng thái DRAFT");
        }
        if (poItemRepository.findByPurchaseOrderId(id).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cần ít nhất một dòng hàng trước khi duyệt đơn nhập");
        }
        purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    // Hủy đơn nhập khi chưa hoàn tất.
    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể hủy đơn nhập đã hoàn tất");
        }
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Đơn nhập đã hủy trước đó");
        }
        purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    // Tìm thực thể đơn nhập theo id.
    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
    }

    // Kiểm tra đơn nhập chưa ở trạng thái kết thúc.
    private void requirePoNotFinished(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.COMPLETED
                || purchaseOrder.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Đơn nhập đã kết thúc, không thể chỉnh sửa");
        }
    }

    // Sinh mã PO duy nhất.
    private String generateUniquePoNumber() {
        for (int i = 0; i < 10; i++) {
            String candidate = CodeGenerator.generate("PO");
            if (!purchaseOrderRepository.existsByPoNumber(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Không thể sinh mã đơn nhập duy nhất, vui lòng thử lại");
    }

}
