package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;

    public List<SalesOrderResponse> findAll() {
        return salesOrderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SalesOrderResponse findById(UUID id) {
        return toResponse(getSalesOrder(id));
    }

    public SalesOrderResponse findBySoNumber(String soNumber) {
        return toResponse(salesOrderRepository.findBySoNumber(soNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Sales order not found")));
    }

    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request) {
        if (salesOrderRepository.existsBySoNumber(request.soNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "SO number already exists");
        }

        SalesOrder salesOrder = SalesOrder.builder()
                .soNumber(request.soNumber())
                .build();

        applyRequest(salesOrder, request.customerName(), request.shippingAddress(), request.warehouseId(),
                request.priority(), request.status());

        return toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public SalesOrderResponse update(UUID id, UpdateSalesOrderRequest request) {
        SalesOrder salesOrder = getSalesOrder(id);

        salesOrderRepository.findBySoNumber(request.soNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "SO number already exists");
                });

        salesOrder.setSoNumber(request.soNumber());
        applyRequest(salesOrder, request.customerName(), request.shippingAddress(), request.warehouseId(),
                request.priority(), request.status());

        return toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public void delete(UUID id) {
        SalesOrder salesOrder = getSalesOrder(id);
        salesOrderRepository.delete(salesOrder);
    }

    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Sales order not found"));
    }

    private void applyRequest(SalesOrder salesOrder,
                              String customerName,
                              java.util.Map<String, Object> shippingAddress,
                              UUID warehouseId,
                              Short priority,
                              String status) {
        salesOrder.setCustomerName(customerName);
        salesOrder.setShippingAddress(shippingAddress);
        salesOrder.setWarehouseId(warehouseId);
        salesOrder.setPriority(priority == null ? (short) 5 : priority);
        salesOrder.setStatus(status == null || status.isBlank() ? "PENDING" : status);
    }

    private SalesOrderResponse toResponse(SalesOrder salesOrder) {
        return new SalesOrderResponse(
                salesOrder.getId(),
                salesOrder.getSoNumber(),
                salesOrder.getCustomerName(),
                salesOrder.getShippingAddress(),
                salesOrder.getWarehouseId(),
                salesOrder.getPriority(),
                salesOrder.getStatus(),
                salesOrder.getCreatedAt()
        );
    }
}