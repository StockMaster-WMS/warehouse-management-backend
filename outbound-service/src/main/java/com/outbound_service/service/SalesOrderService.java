package com.outbound_service.service;

import com.outbound_service.dto.request.SalesOrderActionRequest;
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

    // Lấy danh sách đơn xuất có phân trang và bộ lọc.
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

    // Lấy đơn xuất theo id.
    public SalesOrderResponse findById(UUID id) {
        return salesOrderMapper.toResponse(getSalesOrder(id));
    }

    // Lấy đơn xuất theo mã số đơn.
    public SalesOrderResponse findBySoNumber(String soNumber) {
        return salesOrderMapper.toResponse(salesOrderRepository.findBySoNumber(soNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất")));
    }

    // Tạo mới đơn xuất và sinh mã đơn tự động.
    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request) {
        SalesOrder salesOrder = salesOrderMapper.toEntity(request);
        salesOrder.setSoNumber(CodeGenerator.generate(SO_NUMBER_PREFIX));

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    // Cập nhật thông tin đơn xuất khi đang ở trạng thái PENDING.
    @Transactional
    public SalesOrderResponse update(UUID id, UpdateSalesOrderRequest request) {
        SalesOrder salesOrder = getSalesOrder(id);
        requireStatus(salesOrder, SalesOrderStatus.DRAFT, "Chỉ cập nhật đơn xuất khi đang DRAFT");

        salesOrderRepository.findBySoNumber(request.soNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
                });

        salesOrderMapper.updateEntity(request, salesOrder);
        salesOrder.setSoNumber(request.soNumber().trim());

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    // Xóa đơn xuất khi chưa phát sinh picking.
    @Transactional
    public void delete(UUID id) {
        SalesOrder salesOrder = getSalesOrder(id);
        requireStatus(salesOrder, SalesOrderStatus.DRAFT, "Chỉ xóa đơn xuất khi đang DRAFT");
        if (pickingItemRepository.existsBySoItem_SalesOrder_Id(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không xóa đơn đã có picking; xóa picking trước");
        }
        salesOrderRepository.delete(salesOrder);
    }

    // Chuyển đơn từ PENDING sang PICKING.
    @Transactional
    public SalesOrderResponse startPicking(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PENDING, "Chỉ chuyển sang PICKING khi đơn đang PENDING");

        if (!salesOrderItemRepository.existsBySalesOrder_Id(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một dòng đơn (so-item) trước khi picking");
        }
        so.setStatus(SalesOrderStatus.PICKING);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    // Xác nhận đơn đã pick xong (PICKING -> PACKED)
    @Transactional
    public SalesOrderResponse markPacked(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.PICKING, "Chỉ đóng gói khi đơn đang PICKING");

        ensurePickingCompleted(id);

        so.setStatus(SalesOrderStatus.PACKED);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    // Tạm dừng xử lý đơn xuất khi trạng thái cho phép.
    @Transactional
    public SalesOrderResponse hold(UUID id) {
        SalesOrder so = getSalesOrder(id);
        if (!Set.of(SalesOrderStatus.DRAFT, SalesOrderStatus.PENDING, SalesOrderStatus.PICKING, SalesOrderStatus.PACKED)
                .contains(so.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ tạm dừng đơn chưa giao hàng");
        }
        so.setStatus(SalesOrderStatus.ON_HOLD);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    // Tiếp tục xử lý đơn từ trạng thái ON_HOLD.
    @Transactional
    public SalesOrderResponse resume(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.ON_HOLD, "Chỉ tiếp tục khi đơn đang ON_HOLD");

        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            so.setStatus(SalesOrderStatus.PENDING);
        } else if (isPickingCompleted(id, picks)) {
            so.setStatus(SalesOrderStatus.PACKED);
        } else {
            so.setStatus(SalesOrderStatus.PICKING);
        }
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    // Hủy đơn xuất và giải phóng lượng reserved đã giữ.
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

    // Xác nhận giao hàng, trừ tồn kho và cập nhật shippedQty.
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

    // Xác nhận đơn nháp trước khi xử lý (DRAFT -> PENDING).
    @Transactional
    public SalesOrderResponse confirmOrder(UUID id) {
        SalesOrder so = getSalesOrder(id);
        requireStatus(so, SalesOrderStatus.DRAFT, "Chỉ xác nhận đơn khi đang DRAFT");
        so.setStatus(SalesOrderStatus.PENDING);
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    // Thực thi hành động tập trung.
    @Transactional
    public SalesOrderResponse executeAction(UUID id, SalesOrderActionRequest request) {
        String action = request.getAction().toLowerCase();
        return switch (action) {
            case "confirm" -> confirmOrder(id);
            case "start-picking" -> startPicking(id);
            case "mark-packed" -> markPacked(id);
            case "mark-shipped" -> markShipped(id);
            case "hold" -> hold(id);
            case "resume" -> resume(id);
            case "cancel" -> cancel(id);
            default -> throw new AppException(ErrorCode.BAD_REQUEST, "Hành động (action) không tồn tại: " + action);
        };
    }

    // ==================== private helpers ====================

    // Kiểm tra trạng thái hiện tại của đơn có đúng kỳ vọng.
    private void requireStatus(SalesOrder so, SalesOrderStatus expected, String errorMessage) {
        if (so.getStatus() != expected) {
            throw new AppException(ErrorCode.BAD_REQUEST, errorMessage);
        }
    }

    // Trừ reserved và trừ on-hand theo số lượng đã pick.
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
                    p.getLotNumber(),
                    -picked,
                    stockMutationKey(so.getId(), p.getId(), "SHIP_RELEASE",
                            p.getLocationId(), p.getProductId(), p.getLotNumber(), picked),
                    "SALES_ORDER",
                    so.getId()));
            StockAdjustCommand cmd = new StockAdjustCommand(
                    so.getWarehouseId(),
                    p.getLocationId(),
                    p.getProductId(),
                    p.getLotNumber(),
                    -picked,
                    stockMutationKey(so.getId(), p.getId(), "SHIP_ADJUST",
                            p.getLocationId(), p.getProductId(), p.getLotNumber(), picked),
                    "SALES_ORDER",
                    so.getId());
            warehouseStockGateway.adjustOrThrow(cmd);
        }
    }

    // Tổng hợp picked theo từng dòng đơn và cập nhật shippedQty.
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

    // Đảm bảo toàn bộ picking của đơn đã hoàn tất.
    private void ensurePickingCompleted(UUID salesOrderId) {
        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(salesOrderId);
        validatePickingCompleted(salesOrderId, picks);
    }

    // Kiểm tra điều kiện complete của tất cả picking và từng dòng đơn.
    private boolean isPickingCompleted(UUID salesOrderId, List<PickingItem> picks) {
        try {
            validatePickingCompleted(salesOrderId, picks);
            return true;
        } catch (AppException ex) {
            return false;
        }
    }

    private void validatePickingCompleted(UUID salesOrderId, List<PickingItem> picks) {
        if (picks.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chưa pick đủ: chưa có picking item nào cho đơn xuất này");
        }
        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            int qtyToPick = p.getQtyToPick() == null ? 0 : p.getQtyToPick();
            if (p.getStatus() != PickingItemStatus.PICKED || picked != qtyToPick) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chưa pick đủ cho line " + p.getSoItem().getLineNumber()
                                + ": picking " + p.getId() + " đang ở trạng thái " + p.getStatus()
                                + " với số lượng " + picked + "/" + qtyToPick);
            }
        }

        Map<UUID, Integer> pickedBySoItem = picks.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getSoItem().getId(),
                        Collectors.summingInt(p -> p.getQtyPicked() == null ? 0 : p.getQtyPicked())));
        List<SalesOrderItem> lines = salesOrderItemRepository.findBySalesOrder_Id(salesOrderId);
        for (SalesOrderItem line : lines) {
            int orderedQty = line.getOrderedQty() == null ? 0 : line.getOrderedQty();
            int sum = pickedBySoItem.getOrDefault(line.getId(), 0);
            if (sum < orderedQty) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chưa pick đủ cho line " + line.getLineNumber()
                                + ": đã pick " + sum + "/" + orderedQty);
            }
            if (sum > orderedQty) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Số lượng pick vượt orderedQty ở line " + line.getLineNumber()
                                + ": đã pick " + sum + "/" + orderedQty);
            }
        }
    }

    // Giải phóng reserved cho toàn bộ picking thuộc đơn xuất.
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
                    -qtyToRelease,
                    stockMutationKey(so.getId(), p.getId(), "CANCEL_RELEASE",
                            p.getLocationId(), p.getProductId(), p.getLotNumber(), qtyToRelease),
                    "SALES_ORDER",
                    so.getId()));
        }
    }

    // Tìm thực thể đơn xuất theo id, ném lỗi nếu không tồn tại.
    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }

    private static String stockMutationKey(UUID salesOrderId, UUID pickingItemId, String action,
            UUID locationId, UUID productId, String lotNumber, int qty) {
        String lot = lotNumber == null ? "" : lotNumber.trim();
        return "SALES_ORDER:" + salesOrderId + ":PICKING_ITEM:" + pickingItemId + ":" + action
                + ":" + locationId + ":" + productId + ":" + lot + ":" + qty;
    }
}
