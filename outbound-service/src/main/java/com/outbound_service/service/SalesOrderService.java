package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
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

    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request) {
        SalesOrder salesOrder = SalesOrder.builder()
                .soNumber(request.soNumber())
                .customerName(request.customerName())
                .shippingAddress(request.shippingAddress())
                .warehouseId(request.warehouseId())
                .priority(request.priority() == null ? (short) 5 : request.priority())
                .status(request.status() == null || request.status().isBlank() ? "PENDING" : request.status())
                .build();

        return toResponse(salesOrderRepository.save(salesOrder));
    }

    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Sales order not found"));
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