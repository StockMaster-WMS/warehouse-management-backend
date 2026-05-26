package com.outbound_service.service;

import com.outbound_service.dto.request.SalesOrderActionRequest;
import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.notification.NotificationService;
import com.common.notification.NotificationSeverity;
import com.common.notification.NotificationType;
import com.common.util.CodeGenerator;
import com.warehouse_service.service.StockLevelService;
import com.warehouse_service.service.WarehouseAccessService;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.Customer;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.SalesOrderMapper;
import com.outbound_service.repository.CustomerRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
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
    private final StockLevelService stockLevelService;
    private final SalesOrderMapper salesOrderMapper;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final CustomerRepository customerRepository;
    private final WarehouseAccessService warehouseAccessService;

    // Lấy danh sách đơn xuất có phân trang và bộ lọc.
    public PagedResponse<SalesOrderResponse> findAll(Pageable pageable, String keyword, String status,
            UUID warehouseId, OffsetDateTime createdFrom, OffsetDateTime createdTo,
            Collection<UUID> visibleWarehouseIds) {
        Specification<SalesOrder> spec = SalesOrderSpecification.warehouseIdIn(visibleWarehouseIds)
                .and(SalesOrderSpecification.hasKeyword(keyword))
                .and(SalesOrderSpecification.hasStatus(status))
                .and(SalesOrderSpecification.hasWarehouseId(warehouseId))
                .and(SalesOrderSpecification.createdFrom(createdFrom))
                .and(SalesOrderSpecification.createdTo(createdTo));
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
    public SalesOrderResponse findById(UUID id, Collection<UUID> visibleWarehouseIds) {
        SalesOrder order = getSalesOrder(id);
        assertVisible(order, visibleWarehouseIds);
        return salesOrderMapper.toResponse(order);
    }

    // Lấy đơn xuất theo mã số đơn.
    public SalesOrderResponse findBySoNumber(String soNumber, Collection<UUID> visibleWarehouseIds) {
        SalesOrder order = salesOrderRepository.findBySoNumber(soNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
        assertVisible(order, visibleWarehouseIds);
        return salesOrderMapper.toResponse(order);
    }

    // Tạo mới đơn xuất và sinh mã đơn tự động.
    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request, org.springframework.security.core.Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        SalesOrder salesOrder = salesOrderMapper.toEntity(request);
        applyCustomerFromCatalog(request.customerId(), salesOrder);
        salesOrder.setSoNumber(CodeGenerator.generate(SO_NUMBER_PREFIX));

        SalesOrder saved = salesOrderRepository.save(salesOrder);
        SalesOrderResponse response = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "CREATE", "Tạo đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), null, response,
                null, orderMetadata(saved));
        notificationService.createForRoles(
                List.of("ADMIN", "WAREHOUSE_MANAGER"),
                NotificationType.SYSTEM_ALERT,
                NotificationSeverity.INFO,
                "Có đơn xuất mới",
                "Đơn xuất " + saved.getSoNumber() + " vừa được tạo",
                "SALES_ORDER",
                saved.getId());
        return response;
    }

    // Cập nhật thông tin đơn xuất khi đang ở trạng thái PENDING.
    @Transactional
    public SalesOrderResponse update(UUID id, UpdateSalesOrderRequest request, org.springframework.security.core.Authentication authentication) {
        SalesOrder salesOrder = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, salesOrder.getWarehouseId());
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        requireStatus(salesOrder, SalesOrderStatus.DRAFT, "Chỉ cập nhật đơn xuất khi đang DRAFT");
        SalesOrderResponse before = salesOrderMapper.toResponse(salesOrder);

        salesOrderRepository.findBySoNumber(request.soNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
                });

        salesOrderMapper.updateEntity(request, salesOrder);
        applyCustomerFromCatalog(request.customerId(), salesOrder);
        salesOrder.setSoNumber(request.soNumber().trim());

        SalesOrder saved = salesOrderRepository.save(salesOrder);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "UPDATE", "Cập nhật đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Xóa đơn xuất khi chưa phát sinh picking.
    @Transactional
    public void delete(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder salesOrder = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, salesOrder.getWarehouseId());
        requireStatus(salesOrder, SalesOrderStatus.DRAFT, "Chỉ xóa đơn xuất khi đang DRAFT");
        if (pickingItemRepository.existsBySoItem_SalesOrder_Id(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không xóa đơn đã có picking; xóa picking trước");
        }
        SalesOrderResponse before = salesOrderMapper.toResponse(salesOrder);
        salesOrderRepository.delete(salesOrder);
        auditLogService.record("SALES_ORDER", "DELETE", "Xóa đơn xuất",
                "SALES_ORDER", id, before.soNumber(), before, null,
                null, orderMetadata(salesOrder));
    }

    // Chuyển đơn từ PENDING sang PICKING.
    @Transactional
    public SalesOrderResponse startPicking(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        requireStatus(so, SalesOrderStatus.PENDING, "Chỉ chuyển sang PICKING khi đơn đang PENDING");
        SalesOrderResponse before = salesOrderMapper.toResponse(so);

        if (!salesOrderItemRepository.existsBySalesOrder_Id(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một dòng đơn (so-item) trước khi picking");
        }
        so.setStatus(SalesOrderStatus.PICKING);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "START_PICKING", "Bắt đầu picking đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Xác nhận đơn đã pick xong (PICKING -> PACKED)
    @Transactional
    public SalesOrderResponse markPacked(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        requireStatus(so, SalesOrderStatus.PICKING, "Chỉ đóng gói khi đơn đang PICKING");
        SalesOrderResponse before = salesOrderMapper.toResponse(so);

        ensurePickingCompleted(id);

        so.setStatus(SalesOrderStatus.PACKED);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "PACK", "Đóng gói đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Tạm dừng xử lý đơn xuất khi trạng thái cho phép.
    @Transactional
    public SalesOrderResponse hold(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        SalesOrderResponse before = salesOrderMapper.toResponse(so);
        if (!Set.of(SalesOrderStatus.DRAFT, SalesOrderStatus.PENDING, SalesOrderStatus.PICKING, SalesOrderStatus.PACKED)
                .contains(so.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ tạm dừng đơn chưa giao hàng");
        }
        so.setStatus(SalesOrderStatus.ON_HOLD);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "HOLD", "Tạm dừng đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Tiếp tục xử lý đơn từ trạng thái ON_HOLD.
    @Transactional
    public SalesOrderResponse resume(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        requireStatus(so, SalesOrderStatus.ON_HOLD, "Chỉ tiếp tục khi đơn đang ON_HOLD");
        SalesOrderResponse before = salesOrderMapper.toResponse(so);

        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            so.setStatus(SalesOrderStatus.PENDING);
        } else if (isPickingCompleted(id, picks)) {
            so.setStatus(SalesOrderStatus.PACKED);
        } else {
            so.setStatus(SalesOrderStatus.PICKING);
        }
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "RESUME", "Tiếp tục đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Hủy đơn xuất và giải phóng lượng reserved đã giữ.
    @Transactional
    public SalesOrderResponse cancel(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        SalesOrderResponse before = salesOrderMapper.toResponse(so);
        if (so.getStatus() == SalesOrderStatus.SHIPPED || so.getStatus() == SalesOrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hủy đơn đã xuất kho hoặc đã hoàn tất");
        }
        if (so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn xuất đã hủy trước đó");
        }

        releaseReservedForOrder(so);
        so.setStatus(SalesOrderStatus.CANCELLED);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "CANCEL", "Hủy đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Xác nhận giao hàng, trừ tồn kho và cập nhật shippedQty.
    @Transactional
    public SalesOrderResponse markShipped(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        requireStatus(so, SalesOrderStatus.PACKED, "Chỉ giao hàng khi đơn đang PACKED");
        SalesOrderResponse before = salesOrderMapper.toResponse(so);

        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có picking để xuất kho");
        }

        validatePickingCompleted(id, picks);
        deductStock(so, picks);
        updateShippedQuantities(id, picks);

        so.setStatus(SalesOrderStatus.SHIPPED);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "SHIP", "Xuất kho đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Hoàn tất đơn sau khi đã xuất kho.
    @Transactional
    public SalesOrderResponse completeOrder(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        if (so.getStatus() == SalesOrderStatus.COMPLETED) {
            return salesOrderMapper.toResponse(so);
        }
        requireStatus(so, SalesOrderStatus.SHIPPED, "Chỉ hoàn tất đơn sau khi đã xuất kho");
        SalesOrderResponse before = salesOrderMapper.toResponse(so);
        so.setStatus(SalesOrderStatus.COMPLETED);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "COMPLETE", "Hoàn tất đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    @Transactional
    public SalesOrderResponse confirmOrder(UUID id, org.springframework.security.core.Authentication authentication) {
        SalesOrder so = getSalesOrder(id);
        warehouseAccessService.assertCanAccessWarehouse(authentication, so.getWarehouseId());
        requireStatus(so, SalesOrderStatus.DRAFT, "Chỉ xác nhận đơn khi đang DRAFT");
        SalesOrderResponse before = salesOrderMapper.toResponse(so);
        so.setStatus(SalesOrderStatus.PENDING);
        SalesOrder saved = salesOrderRepository.save(so);
        SalesOrderResponse after = salesOrderMapper.toResponse(saved);
        auditLogService.record("SALES_ORDER", "APPROVE", "Duyệt đơn xuất",
                "SALES_ORDER", saved.getId(), saved.getSoNumber(), before, after,
                null, orderMetadata(saved));
        return after;
    }

    // Thực thi hành động tập trung.
    @Transactional
    public SalesOrderResponse executeAction(UUID id, SalesOrderActionRequest request, org.springframework.security.core.Authentication authentication) {
        String action = request.getAction().toLowerCase();
        return switch (action) {
            case "confirm" -> confirmOrder(id, authentication);
            case "start-picking" -> startPicking(id, authentication);
            case "mark-packed" -> markPacked(id, authentication);
            case "mark-shipped", "ship" -> markShipped(id, authentication);
            case "complete", "mark-completed", "mark-delivered", "delivered" -> completeOrder(id, authentication);
            case "hold" -> hold(id, authentication);
            case "resume" -> resume(id, authentication);
            case "cancel" -> cancel(id, authentication);
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

    // Shipment là điểm duy nhất chuyển reserved thành xuất kho thật:
    // nhả reserved và trừ on-hand theo số lượng đã pick.
    private void deductStock(SalesOrder so, List<PickingItem> picks) {
        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            if (picked <= 0) {
                continue;
            }
            stockLevelService.adjustReserved(new StockReserveCommand(
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
            stockLevelService.adjust(cmd);
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
            stockLevelService.adjustReserved(new StockReserveCommand(
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

    private void assertVisible(SalesOrder order, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null && !visibleWarehouseIds.contains(order.getWarehouseId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân công quản lý kho này");
        }
    }

    private static String stockMutationKey(UUID salesOrderId, UUID pickingItemId, String action,
            UUID locationId, UUID productId, String lotNumber, int qty) {
        String lot = lotNumber == null ? "" : lotNumber.trim();
        return "SALES_ORDER:" + salesOrderId + ":PICKING_ITEM:" + pickingItemId + ":" + action
                + ":" + locationId + ":" + productId + ":" + lot + ":" + qty;
    }

    private Map<String, Object> orderMetadata(SalesOrder order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("soNumber", order.getSoNumber());
        metadata.put("warehouseId", order.getWarehouseId());
        metadata.put("customerId", order.getCustomerId());
        metadata.put("customerName", order.getCustomerName());
        metadata.put("status", order.getStatus() == null ? null : order.getStatus().name());
        return metadata;
    }

    private void applyCustomerFromCatalog(UUID customerId, SalesOrder salesOrder) {
        if (customerId == null) {
            return;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khách hàng"));
        salesOrder.setCustomerId(customer.getId());
        salesOrder.setCustomerName(customer.getName());
    }
}
