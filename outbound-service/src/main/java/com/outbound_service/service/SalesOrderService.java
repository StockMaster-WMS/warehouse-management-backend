package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.mapper.SalesOrderMapper;
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
    private final SalesOrderMapper salesOrderMapper;

    public List<SalesOrderResponse> findAll() {
        return salesOrderRepository.findAll()
                .stream()
                .map(salesOrderMapper::toResponse)
                .toList();
    }

    public SalesOrderResponse findById(UUID id) {
        return salesOrderMapper.toResponse(getSalesOrder(id));
    }

    public SalesOrderResponse findBySoNumber(String soNumber) {
        return salesOrderMapper.toResponse(salesOrderRepository.findBySoNumber(soNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất")));
    }

    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request) {
        if (salesOrderRepository.existsBySoNumber(request.soNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
        }

        SalesOrder salesOrder = salesOrderMapper.toEntity(request);

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public SalesOrderResponse update(UUID id, UpdateSalesOrderRequest request) {
        SalesOrder salesOrder = getSalesOrder(id);

        salesOrderRepository.findBySoNumber(request.soNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
                });

        salesOrderMapper.updateEntity(request, salesOrder);

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public void delete(UUID id) {
        SalesOrder salesOrder = getSalesOrder(id);
        salesOrderRepository.delete(salesOrder);
    }

    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }

}