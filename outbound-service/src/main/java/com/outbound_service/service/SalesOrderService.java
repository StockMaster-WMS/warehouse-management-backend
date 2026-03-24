package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.client.warehouse.WarehouseStockGateway;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.SalesOrderMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.outbound_service.repository.SalesOrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemRepository pickingItemRepository;
    private final WarehouseStockGateway warehouseStockGateway;
    private final SalesOrderMapper salesOrderMapper;

    public PagedResponse<SalesOrderResponse> findAll(Pageable pageable, String keyword, String status, UUID warehouseId) {
        Specification<SalesOrder> spec = SalesOrderSpecification.hasKeyword(keyword)
                .and(SalesOrderSpecification.hasStatus(status))
                .and(SalesOrderSpecification.hasWarehouseId(warehouseId));
        Page<SalesOrder> page = salesOrderRepository.findAll(spec, pageable);
        Page<SalesOrderResponse> mapped = page.map(salesOrderMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
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

    @Transactional
    public void notifyPickingStartedIfPending(UUID salesOrderId) {
        SalesOrder so = getSalesOrder(salesOrderId);
        if (so.getStatus() == SalesOrderStatus.PENDING) {
            so.setStatus(SalesOrderStatus.PICKING);
            salesOrderRepository.save(so);
        }
    }

    @Transactional
    public SalesOrderResponse startPicking(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PENDING, "Chỉ chuyển sang PICKING khi đơn đang PENDING");

        if (salesOrderItemRepository.findBySalesOrder_Id(id).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một dòng đơn (so-item) trước khi picking");
        }
        so.setStatus(SalesOrderStatus.PICKING);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    @Transactional
    public SalesOrderResponse markPacked(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PICKING, "Chỉ đóng gói khi đơn đang PICKING");

        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chưa có picking item cho đơn này");
        }
        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            if (p.getStatus() != PickingItemStatus.PICKED || picked < p.getQtyToPick()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chưa pick đủ: kiểm tra trạng thái PICKED và qtyPicked cho từng dòng picking");
            }
        }
        so.setStatus(SalesOrderStatus.PACKED);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    @Transactional
    public SalesOrderResponse markShipped(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PACKED, "Chỉ giao hàng khi đơn đang PACKED");

        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có picking để xuất kho");
        }

        deductStock(so, picks);
        updateShippedQuantities(id, picks);

        so.setStatus(SalesOrderStatus.SHIPPED);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    // ==================== private helpers ====================

    private void requireStatus(SalesOrder so, SalesOrderStatus expected, String errorMessage) {
        if (so.getStatus() != expected) {
            throw new AppException(ErrorCode.BAD_REQUEST, errorMessage);
        }
    }

    private void deductStock(SalesOrder so, List<PickingItem> picks) {
        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            if (picked <= 0) {
                continue;
            }
            StockAdjustCommand cmd = new StockAdjustCommand(
                    so.getWarehouseId(),
                    p.getLocationId(),
                    p.getProductId(),
                    null,
                    -picked);
            warehouseStockGateway.adjustOrThrow(cmd);
        }
    }

    private void updateShippedQuantities(UUID salesOrderId, List<PickingItem> picks) {
        Map<UUID, Integer> pickedBySoItem = picks.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getSoItem().getId(),
                        Collectors.summingInt(p -> p.getQtyPicked() == null ? 0 : p.getQtyPicked())));

        List<SalesOrderItem> lines = salesOrderItemRepository.findBySalesOrder_Id(salesOrderId);
        for (SalesOrderItem line : lines) {
            line.setShippedQty(pickedBySoItem.getOrDefault(line.getId(), 0));
        }
        salesOrderItemRepository.saveAll(lines);
    }

    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }
}
