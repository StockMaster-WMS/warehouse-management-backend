package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.outbound_service.client.WarehouseStockGateway;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    private static final String SO_NUMBER_PREFIX = "SO";

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemRepository pickingItemRepository;
    private final WarehouseStockGateway warehouseStockGateway;
    private final SalesOrderMapper salesOrderMapper;

    public PagedResponse<SalesOrderResponse> findAll(Pageable pageable, String keyword, String status,
            UUID warehouseId) {
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
        SalesOrder salesOrder = salesOrderMapper.toEntity(request);
        salesOrder.setSoNumber(CodeGenerator.generate(SO_NUMBER_PREFIX));

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public SalesOrderResponse update(UUID id, UpdateSalesOrderRequest request) {
        SalesOrder salesOrder = getSalesOrder(id);
        requireStatus(salesOrder, SalesOrderStatus.PENDING, "Chỉ cập nhật đơn xuất khi đang PENDING");

        salesOrderRepository.findBySoNumber(request.soNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
                });

        salesOrderMapper.updateEntity(request, salesOrder);
        salesOrder.setSoNumber(request.soNumber().trim());

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public void delete(UUID id) {
        SalesOrder salesOrder = getSalesOrder(id);
        requireStatus(salesOrder, SalesOrderStatus.PENDING, "Chỉ xóa đơn xuất khi đang PENDING");
        if (pickingItemRepository.existsBySoItem_SalesOrder_Id(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không xóa đơn đã có picking; xóa picking trước");
        }
        salesOrderRepository.delete(salesOrder);
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
    public SalesOrderResponse markPicked(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PICKING, "Chỉ xác nhận PICKED khi đơn đang PICKING");

        ensurePickingCompleted(id);

        so.setStatus(SalesOrderStatus.PICKED);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    @Transactional
    public SalesOrderResponse markPacked(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PICKED, "Chỉ đóng gói khi đơn đang PICKED");

        ensurePickingCompleted(id);

        so.setStatus(SalesOrderStatus.PACKED);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    @Transactional
    public SalesOrderResponse hold(UUID id) {
        SalesOrder so = getSalesOrder(id);
        if (!Set.of(SalesOrderStatus.PENDING, SalesOrderStatus.PICKING, SalesOrderStatus.PICKED, SalesOrderStatus.PACKED)
                .contains(so.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ tạm dừng đơn chưa giao hàng");
        }
        so.setStatus(SalesOrderStatus.ON_HOLD);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    @Transactional
    public SalesOrderResponse resume(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.ON_HOLD, "Chỉ tiếp tục khi đơn đang ON_HOLD");

        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            so.setStatus(SalesOrderStatus.PENDING);
        } else if (isPickingCompleted(id, picks)) {
            so.setStatus(SalesOrderStatus.PICKED);
        } else {
            so.setStatus(SalesOrderStatus.PICKING);
        }
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    @Transactional
    public SalesOrderResponse cancel(UUID id) {
        SalesOrder so = getSalesOrder(id);
        if (so.getStatus() == SalesOrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hủy đơn đã giao hàng");
        }
        if (so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn xuất đã hủy trước đó");
        }

        releaseReservedForOrder(so);
        so.setStatus(SalesOrderStatus.CANCELLED);
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
            warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                    so.getWarehouseId(),
                    p.getLocationId(),
                    p.getProductId(),
                    null,
                    -picked));
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

    private void ensurePickingCompleted(UUID salesOrderId) {
        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(salesOrderId);
        if (!isPickingCompleted(salesOrderId, picks)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chưa pick đủ: kiểm tra trạng thái PICKED và qtyPicked theo từng dòng đơn");
        }
    }

    private boolean isPickingCompleted(UUID salesOrderId, List<PickingItem> picks) {
        if (picks.isEmpty()) {
            return false;
        }
        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            if (p.getStatus() != PickingItemStatus.PICKED || picked < p.getQtyToPick()) {
                return false;
            }
        }

        Map<UUID, Integer> pickedBySoItem = picks.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getSoItem().getId(),
                        Collectors.summingInt(p -> p.getQtyPicked() == null ? 0 : p.getQtyPicked())));
        List<SalesOrderItem> lines = salesOrderItemRepository.findBySalesOrder_Id(salesOrderId);
        for (SalesOrderItem line : lines) {
            int sum = pickedBySoItem.getOrDefault(line.getId(), 0);
            if (sum < line.getOrderedQty()) {
                return false;
            }
        }
        return true;
    }

    private void releaseReservedForOrder(SalesOrder so) {
        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(so.getId());
        for (PickingItem p : picks) {
            int qtyToRelease = p.getQtyToPick() == null ? 0 : p.getQtyToPick();
            if (qtyToRelease <= 0) {
                continue;
            }
            warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                    so.getWarehouseId(),
                    p.getLocationId(),
                    p.getProductId(),
                    p.getLotNumber(),
                    -qtyToRelease));
        }
    }

    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }
}
