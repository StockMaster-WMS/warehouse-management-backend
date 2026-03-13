package com.inbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
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

    public PurchaseOrderResponse findByPoNumber(String poNumber) {
        return toResponse(purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Purchase order not found")));
    }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        if (purchaseOrderRepository.existsByPoNumber(request.poNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PO number already exists");
        }

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .poNumber(request.poNumber())
                .build();

        applyRequest(purchaseOrder, request.supplierId(), request.warehouseId(), request.status(), request.orderDate(),
                request.expectedDate(), request.totalAmount());

        return toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderResponse update(UUID id, UpdatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);

        purchaseOrderRepository.findByPoNumber(request.poNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "PO number already exists");
                });

        purchaseOrder.setPoNumber(request.poNumber());
        applyRequest(purchaseOrder, request.supplierId(), request.warehouseId(), request.status(), request.orderDate(),
                request.expectedDate(), request.totalAmount());

        return toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    @Transactional
    public void delete(UUID id) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);
        purchaseOrderRepository.delete(purchaseOrder);
    }

    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Purchase order not found"));
    }

    private void applyRequest(PurchaseOrder purchaseOrder,
                              UUID supplierId,
                              UUID warehouseId,
                              String status,
                              java.time.LocalDate orderDate,
                              java.time.LocalDate expectedDate,
                              BigDecimal totalAmount) {
        purchaseOrder.setSupplierId(supplierId);
        purchaseOrder.setWarehouseId(warehouseId);
        purchaseOrder.setStatus(status == null || status.isBlank() ? "DRAFT" : status);
        purchaseOrder.setOrderDate(orderDate);
        purchaseOrder.setExpectedDate(expectedDate);
        purchaseOrder.setTotalAmount(totalAmount == null ? BigDecimal.ZERO : totalAmount);
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