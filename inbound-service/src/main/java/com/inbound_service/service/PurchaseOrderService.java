package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.dto.response.PurchaseOrderDetailResponse;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.mapper.PurchaseOrderMapper;
import com.inbound_service.mapper.PutawayTaskMapper;
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
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PoItemMapper poItemMapper;
    private final PutawayTaskMapper putawayTaskMapper;

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

    public PurchaseOrderResponse findById(UUID id) {
        return purchaseOrderMapper.toResponse(getPurchaseOrder(id));
    }

    public PurchaseOrderResponse findByPoNumber(String poNumber) {
        return purchaseOrderMapper.toResponse(purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập")));
    }

        public PurchaseOrderDetailResponse findDetail(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        List<PoItem> items = poItemRepository.findByPurchaseOrderId(id);
        List<PutawayTask> putawayTasks = putawayTaskRepository.findByPurchaseOrderIdWithPoItem(id);

        List<PoItemResponse> itemResponses = items.stream()
            .map(poItemMapper::toResponse)
            .toList();

        List<PutawayTaskResponse> putawayResponses = putawayTasks.stream()
            .map(putawayTaskMapper::toResponse)
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
            totalOrderedQty,
            totalReceivedQty,
            fullyReceived);
        }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = purchaseOrderMapper.toEntity(request);
        purchaseOrder.setPoNumber(generateUniquePoNumber());

        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

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

    @Transactional
    public void delete(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        requirePoNotFinished(purchaseOrder);
        if (!poItemRepository.findByPurchaseOrderId(id).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể xóa PO đã có dòng hàng");
        }
        purchaseOrderRepository.delete(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderResponse confirm(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ xác nhận đơn nhập ở trạng thái DRAFT");
        }
        if (poItemRepository.findByPurchaseOrderId(id).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cần ít nhất một dòng hàng trước khi xác nhận đơn nhập");
        }
        purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVING);
        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.RECEIVED) {
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

    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
    }

    private void requirePoNotFinished(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.RECEIVED
                || purchaseOrder.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Đơn nhập đã kết thúc, không thể chỉnh sửa");
        }
    }

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
