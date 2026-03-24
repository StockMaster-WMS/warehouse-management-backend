package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.mapper.PurchaseOrderMapper;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PurchaseOrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;

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

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        if (purchaseOrderRepository.existsByPoNumber(request.poNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn nhập đã tồn tại");
        }

        PurchaseOrder purchaseOrder = purchaseOrderMapper.toEntity(request);

        return purchaseOrderMapper.toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderResponse update(UUID id, UpdatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(id);

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
        purchaseOrderRepository.delete(purchaseOrder);
    }

    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
    }

}