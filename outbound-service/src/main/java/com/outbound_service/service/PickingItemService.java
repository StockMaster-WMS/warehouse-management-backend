package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.mapper.PickingItemMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PickingItemService {

    private final PickingItemRepository pickingItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemMapper pickingItemMapper;
    private final SalesOrderService salesOrderService;

    public List<PickingItemResponse> findAll(UUID soItemId, UUID productId, UUID locationId) {
        List<PickingItem> items;

        if (soItemId != null) {
            items = pickingItemRepository.findBySoItem_Id(soItemId);
        } else if (productId != null) {
            items = pickingItemRepository.findByProductId(productId);
        } else if (locationId != null) {
            items = pickingItemRepository.findByLocationId(locationId);
        } else {
            items = pickingItemRepository.findAll();
        }

        return items.stream()
                .filter(item -> soItemId == null || item.getSoItem().getId().equals(soItemId))
                .filter(item -> productId == null || item.getProductId().equals(productId))
                .filter(item -> locationId == null || item.getLocationId().equals(locationId))
                .map(pickingItemMapper::toResponse)
                .toList();
    }

    public PickingItemResponse findById(UUID id) {
        return pickingItemMapper.toResponse(getPickingItem(id));
    }

    @Transactional
    public PickingItemResponse create(CreatePickingItemRequest request) {
        validateQuantities(request.qtyToPick(), request.qtyPicked());
        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrder(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        PickingItem item = pickingItemMapper.toEntity(request);
        item.setSoItem(line);
        PickingItem saved = pickingItemRepository.save(item);

        salesOrderService.notifyPickingStartedIfPending(line.getSalesOrder().getId());

        return pickingItemMapper.toResponse(saved);
    }

    @Transactional
    public PickingItemResponse update(UUID id, UpdatePickingItemRequest request) {
        validateQuantities(request.qtyToPick(), request.qtyPicked());
        PickingItem item = getPickingItem(id);
        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrder(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        pickingItemMapper.updateEntity(request, item);
        item.setSoItem(line);
        return pickingItemMapper.toResponse(pickingItemRepository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        PickingItem item = getPickingItem(id);
        pickingItemRepository.delete(item);
    }

    private PickingItem getPickingItem(UUID id) {
        return pickingItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
    }

    private void validateQuantities(Integer qtyToPick, Integer qtyPicked) {
        int picked = qtyPicked == null ? 0 : qtyPicked;
        if (qtyToPick <= 0 || picked < 0 || picked > qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng pick không hợp lệ");
        }
    }
}