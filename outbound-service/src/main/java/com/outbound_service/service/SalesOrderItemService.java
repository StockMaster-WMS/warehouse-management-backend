package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreateSalesOrderItemRequest;
import com.outbound_service.dto.request.UpdateSalesOrderItemRequest;
import com.outbound_service.dto.response.SalesOrderItemResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.mapper.SalesOrderItemMapper;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderItemService {

    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemMapper salesOrderItemMapper;

    public List<SalesOrderItemResponse> findAll(UUID salesOrderId) {
        List<SalesOrderItem> list = salesOrderId == null
                ? salesOrderItemRepository.findAll()
                : salesOrderItemRepository.findBySalesOrder_Id(salesOrderId);
        return list.stream().map(salesOrderItemMapper::toResponse).toList();
    }

    public SalesOrderItemResponse findById(UUID id) {
        return salesOrderItemMapper.toResponse(getLine(id));
    }

    @Transactional
    public SalesOrderItemResponse create(CreateSalesOrderItemRequest request) {
        SalesOrder order = getOrder(request.salesOrderId());
        ensureLineUnique(request.salesOrderId(), request.lineNumber(), null);

        SalesOrderItem item = salesOrderItemMapper.toEntity(request);
        item.setSalesOrder(order);
        return salesOrderItemMapper.toResponse(salesOrderItemRepository.save(item));
    }

    @Transactional
    public SalesOrderItemResponse update(UUID id, UpdateSalesOrderItemRequest request) {
        SalesOrderItem item = getLine(id);
        SalesOrder order = getOrder(request.salesOrderId());
        ensureLineUnique(request.salesOrderId(), request.lineNumber(), id);

        int shipped = request.shippedQty() == null ? item.getShippedQty() : request.shippedQty();
        if (shipped < 0 || shipped > request.orderedQty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "shipped_qty không hợp lệ");
        }

        salesOrderItemMapper.updateEntity(request, item);
        item.setSalesOrder(order);
        item.setShippedQty(shipped);

        return salesOrderItemMapper.toResponse(salesOrderItemRepository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        SalesOrderItem item = getLine(id);
        salesOrderItemRepository.delete(item);
    }

    private SalesOrderItem getLine(UUID id) {
        return salesOrderItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
    }

    private SalesOrder getOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }

    private void ensureLineUnique(UUID salesOrderId, Short lineNumber, UUID currentId) {
        salesOrderItemRepository.findBySalesOrder_IdAndLineNumber(salesOrderId, lineNumber)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(x -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Số dòng đã tồn tại trên đơn xuất");
                });
    }
}
