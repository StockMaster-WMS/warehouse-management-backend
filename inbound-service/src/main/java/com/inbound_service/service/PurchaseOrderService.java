package com.inbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public List<PurchaseOrderResponse> findAll() {
        return purchaseOrderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PurchaseOrderResponse findById(UUID id) {
        return toResponse(getPurchaseOrder(id));
    }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .poNumber(request.poNumber())
                .supplierId(request.supplierId())
                .warehouseId(request.warehouseId())
                .status(request.status() == null || request.status().isBlank() ? "DRAFT" : request.status())
                .orderDate(request.orderDate())
                .expectedDate(request.expectedDate())
                .totalAmount(request.totalAmount() == null ? BigDecimal.ZERO : request.totalAmount())
                .build();

        return toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Purchase order not found"));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder) {
        return new PurchaseOrderResponse(
                purchaseOrder.getId(),
                purchaseOrder.getPoNumber(),
                purchaseOrder.getSupplierId(),
                purchaseOrder.getWarehouseId(),
                purchaseOrder.getStatus(),
                purchaseOrder.getOrderDate(),
                purchaseOrder.getExpectedDate(),
                purchaseOrder.getTotalAmount(),
                purchaseOrder.getCreatedAt()
        );
    }
}