package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockReserveCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.client.WarehouseStockGateway;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.PickingItemMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.PickingItemSpecification;
import com.outbound_service.repository.SalesOrderItemRepository;
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
public class PickingItemService {

    private final PickingItemRepository pickingItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemMapper pickingItemMapper;
    private final SalesOrderService salesOrderService;
    private final WarehouseStockGateway warehouseStockGateway;

    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId, UUID locationId) {
        Specification<PickingItem> spec = PickingItemSpecification.hasSoItemId(soItemId)
                .and(PickingItemSpecification.hasProductId(productId))
                .and(PickingItemSpecification.hasLocationId(locationId));
        Page<PickingItem> page = pickingItemRepository.findAll(spec, pageable);
        Page<PickingItemResponse> mapped = page.map(pickingItemMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    public PickingItemResponse findById(UUID id) {
        return pickingItemMapper.toResponse(getPickingItem(id));
    }

    @Transactional
    public PickingItemResponse create(CreatePickingItemRequest request) {
        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrder(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        SalesOrder so = line.getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        PickingItemStatus status = parsePickingStatus(request.status());
        validateQuantities(request.qtyToPick(), request.qtyPicked(), status);

        PickingItem item = pickingItemMapper.toEntity(request);
        item.setSoItem(line);
        item.setStatus(status);

        PickingItem saved = pickingItemRepository.save(item);
        salesOrderService.notifyPickingStartedIfPending(so.getId());

        warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                so.getWarehouseId(), request.locationId(), request.productId(), null, request.qtyToPick()));

        if (saved.getStatus() == PickingItemStatus.PICKED) {
            int picked = saved.getQtyPicked() == null ? 0 : saved.getQtyPicked();
            warehouseStockGateway.requireOnHandAtLeast(
                    so.getWarehouseId(), saved.getLocationId(), saved.getProductId(), null, picked);
        }

        return pickingItemMapper.toResponse(saved);
    }

    @Transactional
    public PickingItemResponse update(UUID id, UpdatePickingItemRequest request) {
        PickingItem existing = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        SalesOrder so = existing.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        UUID oldLoc = existing.getLocationId();
        UUID oldProd = existing.getProductId();
        int oldQtyToPick = existing.getQtyToPick();

        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrder(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        pickingItemMapper.updateEntity(request, existing);
        existing.setSoItem(line);
        PickingItemStatus newStatus = parsePickingStatus(request.status());
        existing.setStatus(newStatus);

        validateQuantities(existing.getQtyToPick(), existing.getQtyPicked(), newStatus);

        boolean allocChanged = !oldLoc.equals(existing.getLocationId())
                || !oldProd.equals(existing.getProductId())
                || oldQtyToPick != existing.getQtyToPick();

        if (allocChanged) {
            warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                    so.getWarehouseId(), oldLoc, oldProd, null, -oldQtyToPick));
            warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                    so.getWarehouseId(), existing.getLocationId(), existing.getProductId(), null, existing.getQtyToPick()));
        }

        if (existing.getStatus() == PickingItemStatus.PICKED) {
            int picked = existing.getQtyPicked() == null ? 0 : existing.getQtyPicked();
            warehouseStockGateway.requireOnHandAtLeast(
                    so.getWarehouseId(), existing.getLocationId(), existing.getProductId(), null, picked);
        }

        return pickingItemMapper.toResponse(pickingItemRepository.save(existing));
    }

    @Transactional
    public void delete(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                so.getWarehouseId(), item.getLocationId(), item.getProductId(), null, -item.getQtyToPick()));

        pickingItemRepository.delete(item);
    }

    private PickingItem getPickingItem(UUID id) {
        return pickingItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
    }

    private static void assertSalesOrderAllowsPickingMutation(SalesOrder so) {
        if (so.getStatus() == SalesOrderStatus.PACKED || so.getStatus() == SalesOrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thao tác picking khi đơn đã đóng gói hoặc đã giao");
        }
        if (so.getStatus() == SalesOrderStatus.PICKED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thao tác picking khi đơn đã pick xong");
        }
    }

    private static PickingItemStatus parsePickingStatus(String raw) {
        try {
            return PickingItemStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái picking không hợp lệ: " + raw);
        }
    }

    private static void validateQuantities(Integer qtyToPick, Integer qtyPicked, PickingItemStatus status) {
        int picked = qtyPicked == null ? 0 : qtyPicked;
        if (qtyToPick <= 0 || picked < 0 || picked > qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng pick không hợp lệ");
        }
        if (status == PickingItemStatus.PICKED && picked != qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái PICKED yêu cầu qtyPicked bằng qtyToPick");
        }
    }
}
