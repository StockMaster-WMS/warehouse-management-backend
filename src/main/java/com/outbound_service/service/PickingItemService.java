package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockReserveCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.notification.CreateNotificationCommand;
import com.common.notification.NotificationService;
import com.common.notification.NotificationSeverity;
import com.common.notification.NotificationType;
import com.product_service.entity.Product;
import com.product_service.repository.ProductRepository;
import com.product_service.service.ProductService;
import com.product_service.dto.response.ProductResponse;
import com.auth_service.repository.UserRepository;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.WarehouseRepository;
import com.warehouse_service.service.LocationService;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.service.StockLevelService;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.dto.response.PickingItemDetailResponse;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.mapper.PickingItemMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.PickingItemSpecification;
import com.outbound_service.repository.SalesOrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PickingItemService {

    private final PickingItemRepository pickingItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemMapper pickingItemMapper;
    private final StockLevelService stockLevelService;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductService productService;
    private final LocationService locationService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // Lấy danh sách picking item có phân trang và bộ lọc.
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId,
            UUID locationId, String status, OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        return findAll(pageable, soItemId, productId, locationId, status, createdFrom, createdTo, null);
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId,
            UUID locationId, String status, OffsetDateTime createdFrom, OffsetDateTime createdTo, UUID assigneeId) {
        return findAll(pageable, soItemId, productId, locationId, status, createdFrom, createdTo, assigneeId, null);
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId,
            UUID locationId, String status, OffsetDateTime createdFrom, OffsetDateTime createdTo, UUID assigneeId,
            Set<UUID> visibleWarehouseIds) {
        return findAll(pageable, soItemId, productId, locationId, status, null, createdFrom, createdTo, assigneeId,
                visibleWarehouseIds);
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId,
            UUID locationId, String status, String salesOrderStatus, OffsetDateTime createdFrom, OffsetDateTime createdTo,
            UUID assigneeId, Set<UUID> visibleWarehouseIds) {
        PickingItemStatus pickingStatus = parseOptionalPickingStatus(status);
        SalesOrderStatus orderStatus = parseOptionalSalesOrderStatus(salesOrderStatus);
        Specification<PickingItem> spec = PickingItemSpecification.hasSoItemId(soItemId)
                .and(PickingItemSpecification.hasProductId(productId))
                .and(PickingItemSpecification.hasLocationId(locationId))
                .and(PickingItemSpecification.hasAssigneeId(assigneeId))
                .and(PickingItemSpecification.hasWarehouseIdIn(visibleWarehouseIds))
                .and(PickingItemSpecification.hasStatus(pickingStatus))
                .and(PickingItemSpecification.hasSalesOrderStatus(orderStatus))
                .and(PickingItemSpecification.salesOrderCreatedFrom(createdFrom))
                .and(PickingItemSpecification.salesOrderCreatedTo(createdTo));
        Page<PickingItem> page = pickingItemRepository.findAll(spec, pageable);
        List<PickingItemResponse> mappedRows = page.getContent().stream()
                .map(pickingItemMapper::toResponse)
                .toList();
        Map<UUID, Product> productsById = loadProductsById(mappedRows);
        Map<UUID, Location> locationsById = loadLocationsById(mappedRows);
        Map<UUID, Warehouse> warehousesById = loadWarehousesById(mappedRows);
        List<PickingItemResponse> rows = mappedRows.stream()
                .map(row -> enrichListRow(row, productsById, locationsById, warehousesById))
                .toList();
        return new PagedResponse<>(
                rows,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    // Bổ sung thông tin sản phẩm và vị trí cho dòng hiển thị danh sách.
    private PickingItemResponse enrichListRow(PickingItemResponse row,
            Map<UUID, Product> productsById,
            Map<UUID, Location> locationsById,
            Map<UUID, Warehouse> warehousesById) {
        Product product = productsById.get(row.productId());
        Location location = locationsById.get(row.locationId());
        Warehouse warehouse = warehousesById.get(row.warehouseId());

        String productSku = row.productSku();
        if ((productSku == null || productSku.isBlank()) && product != null) {
            productSku = product.getSku();
        }
        String locationCode = location == null ? String.valueOf(row.locationId()) : location.getCode();

        return new PickingItemResponse(
                row.id(),
                row.soItemId(),
                row.productId(),
                row.locationId(),
                row.lotNumber(),
                row.qtyToPick(),
                row.qtyPicked(),
                row.status(),
                row.pickSequence(),
                row.salesOrderNumber(),
                productSku,
                product == null ? null : product.getName(),
                product == null ? null : product.getBarcodeEan13(),
                locationCode,
                locationCode,
                row.warehouseId(),
                warehouse == null ? null : warehouse.getCode(),
                warehouse == null ? null : warehouse.getName(),
                row.assigneeId());
    }

    private Map<UUID, Product> loadProductsById(List<PickingItemResponse> rows) {
        Set<UUID> ids = new HashSet<>();
        for (PickingItemResponse row : rows) {
            if (row.productId() != null) {
                ids.add(row.productId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Map<UUID, Location> loadLocationsById(List<PickingItemResponse> rows) {
        Set<UUID> ids = new HashSet<>();
        for (PickingItemResponse row : rows) {
            if (row.locationId() != null) {
                ids.add(row.locationId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return locationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
    }

    private Map<UUID, Warehouse> loadWarehousesById(List<PickingItemResponse> rows) {
        Set<UUID> ids = new HashSet<>();
        for (PickingItemResponse row : rows) {
            if (row.warehouseId() != null) {
                ids.add(row.warehouseId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    // Tạo mới picking item và cộng lượng reserved tương ứng.
    @Transactional
    public PickingItemResponse create(CreatePickingItemRequest request) {
        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrderForUpdate(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        SalesOrder so = line.getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        PickingItemStatus status = parsePickingStatus(request.status());
        validateQuantities(request.qtyToPick(), request.qtyPicked(), status);
        validateAllocationAgainstOrderedQty(line, request.qtyToPick(), request.qtyPicked(), null);

        PickingItem item = pickingItemMapper.toEntity(request);
        item.setSoItem(line);
        item.setStatus(status);

        PickingItem saved = pickingItemRepository.save(item);
        
        try {
            stockLevelService.adjustReserved(reserveCommand(
                    so, request.locationId(), request.productId(), normalizeLot(request.lotNumber()),
                    request.qtyToPick(), saved.getId(), reserveMutationKey(saved.getId(), "CREATE",
                            request.locationId(), request.productId(), normalizeLot(request.lotNumber()),
                            request.qtyToPick())));
        } catch (Exception e) {
            log.error("Failed to reserve stock via StockLevelService: {}", e.getMessage());
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể giữ chỗ tồn kho (Reserved). Lỗi: " + e.getMessage());
        }

        PickingItemResponse response = pickingItemMapper.toResponse(saved);
        auditLogService.record("PICKING", "CREATE", "Tạo nhiệm vụ picking",
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), null, response,
                null, pickingMetadata(saved));
        return response;
    }

    // Cập nhật picking item và điều chỉnh lại reserved khi allocation thay đổi.
    @Transactional
    public PickingItemResponse update(UUID id, UpdatePickingItemRequest request) {
        PickingItem existing = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
        PickingItemResponse before = pickingItemMapper.toResponse(existing);

        SalesOrder so = existing.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        UUID oldLoc = existing.getLocationId();
        UUID oldProd = existing.getProductId();
        int oldQtyToPick = existing.getQtyToPick();
        String oldLot = normalizeLot(existing.getLotNumber());

        if (!existing.getSoItem().getId().equals(request.soItemId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được chuyển picking sang dòng đơn xuất khác");
        }

        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrderForUpdate(existing.getSoItem().getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        pickingItemMapper.updateEntity(request, existing);
        existing.setSoItem(line);
        PickingItemStatus newStatus = parsePickingStatus(request.status());
        existing.setStatus(newStatus);
        String newLot = normalizeLot(existing.getLotNumber());
        boolean allocChanged = !Objects.equals(oldLoc, existing.getLocationId())
                || !Objects.equals(oldProd, existing.getProductId())
                || oldQtyToPick != existing.getQtyToPick()
                || !oldLot.equals(newLot);

        validateQuantities(existing.getQtyToPick(), existing.getQtyPicked(), newStatus);
        if (allocChanged) {
            validateAllocationAgainstOrderedQty(line, existing.getQtyToPick(), existing.getQtyPicked(), existing.getId());
        } else {
            validatePickedAgainstOrderedQty(line, existing.getQtyPicked(), existing.getId());
        }

        if (allocChanged) {
            stockLevelService.adjustReserved(reserveCommand(
                    so, oldLoc, oldProd, oldLot, -oldQtyToPick,
                    existing.getId(),
                    reserveMutationKey(existing.getId(), "UPDATE_RELEASE", oldLoc, oldProd, oldLot, oldQtyToPick)));
            stockLevelService.adjustReserved(reserveCommand(
                    so, existing.getLocationId(), existing.getProductId(), newLot, existing.getQtyToPick(),
                    existing.getId(),
                    reserveMutationKey(existing.getId(), "UPDATE_RESERVE",
                            existing.getLocationId(), existing.getProductId(), newLot, existing.getQtyToPick())));
        }

        PickingItem saved = pickingItemRepository.save(existing);
        PickingItemResponse after = pickingItemMapper.toResponse(saved);

        String actionType = saved.getStatus() == PickingItemStatus.PICKED ? "PICK" : "UPDATE";
        String action = saved.getStatus() == PickingItemStatus.PICKED ? "Hoàn tất picking" : "Cập nhật picking";
        auditLogService.record("PICKING", actionType, action,
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), before, after,
                null, pickingMetadata(saved));
        return after;
    }

    // Xóa picking item và hoàn trả lượng reserved đã giữ.
    @Transactional
    public void delete(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
        PickingItemResponse before = pickingItemMapper.toResponse(item);

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        stockLevelService.adjustReserved(reserveCommand(
                so, item.getLocationId(), item.getProductId(), normalizeLot(item.getLotNumber()),
                -item.getQtyToPick(),
                item.getId(),
                reserveMutationKey(item.getId(), "DELETE", item.getLocationId(), item.getProductId(),
                        normalizeLot(item.getLotNumber()), item.getQtyToPick())));

        pickingItemRepository.delete(item);
        auditLogService.record("PICKING", "DELETE", "Xóa nhiệm vụ picking",
                "PICKING_ITEM", id, pickingEntityName(item), before, null,
                null, pickingMetadata(item));
    }

    @Transactional
    public PickingItemResponse assignTask(UUID id, UUID assigneeId) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
        PickingItemResponse before = pickingItemMapper.toResponse(item);

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);
        assertAssigneeCanWorkInWarehouse(assigneeId, so.getWarehouseId());

        item.setAssigneeId(assigneeId);
        boolean shouldNotifyAssignee = assigneeId != null && !assigneeId.equals(before.assigneeId());
        PickingItem saved = pickingItemRepository.save(item);
        if (shouldNotifyAssignee) {
            notifyPickingAssigned(saved);
        }
        
        PickingItemResponse after = pickingItemMapper.toResponse(saved);
        auditLogService.record("PICKING", "ASSIGN", "Phân công nhiệm vụ picking",
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), before, after,
                null, pickingMetadata(saved));
        return after;
    }

    @Transactional
    public PickingItemResponse reportException(UUID id, String reason) {
        return reportException(id, reason, null, true);
    }

    @Transactional
    public PickingItemResponse reportException(UUID id, String reason, UUID actorId, boolean canBypassAssignment) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
        assertPickingAssignmentAllowed(item, actorId, canBypassAssignment);
        PickingItemResponse before = pickingItemMapper.toResponse(item);

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        // Logic: Khi báo lỗi, chuyển lại về PENDING (hoặc trạng thái ngoại lệ nếu có), 
        // tạm thời đặt qtyPicked = 0.
        item.setStatus(PickingItemStatus.PENDING);
        item.setQtyPicked(0);
        PickingItem saved = pickingItemRepository.save(item);
        
        PickingItemResponse after = pickingItemMapper.toResponse(saved);
        
        Map<String, Object> meta = pickingMetadata(saved);
        meta.put("exceptionReason", reason);
        notificationService.createForRoles(
                List.of("ADMIN", "WAREHOUSE_MANAGER"),
                NotificationType.PICKING_EXCEPTION,
                NotificationSeverity.WARNING,
                "Có lỗi picking cần xử lý",
                "Picking item " + saved.getId() + " vừa được báo lỗi: " + (reason == null ? "" : reason),
                "PICKING_ITEM",
                saved.getId());

        auditLogService.record("PICKING", "EXCEPTION", "Báo lỗi lấy hàng: " + reason,
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), before, after,
                null, meta);
        return after;
    }

    @Transactional
    public PickingItemResponse completeMobile(UUID id) {
        return completeMobile(id, null, true);
    }

    @Transactional
    public PickingItemResponse completeMobile(UUID id, UUID actorId, boolean canBypassAssignment) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
        
        assertPickingAssignmentAllowed(item, actorId, canBypassAssignment);
        if (item.getStatus() == PickingItemStatus.PICKED) {
            return pickingItemMapper.toResponse(item); // Đã hoàn tất
        }

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);
        PickingItemResponse before = pickingItemMapper.toResponse(item);

        int qtyToPick = item.getQtyToPick() == null ? 0 : item.getQtyToPick();
        validateQuantities(qtyToPick, qtyToPick, PickingItemStatus.PICKED);
        validatePickedAgainstOrderedQty(item.getSoItem(), qtyToPick, item.getId());

        item.setQtyPicked(qtyToPick);
        item.setStatus(PickingItemStatus.PICKED);
        PickingItem saved = pickingItemRepository.save(item);
        PickingItemResponse after = pickingItemMapper.toResponse(saved);

        auditLogService.record("PICKING", "PICK", "Hoàn tất picking",
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), before, after,
                null, pickingMetadata(saved));
        return after;
    }



    // Kiểm tra trạng thái đơn xuất có cho phép chỉnh sửa nghiệp vụ picking hay không.
    private static void assertSalesOrderAllowsPickingMutation(SalesOrder so) {
        SalesOrderStatus status = so.getStatus();
        // Chỉ cho phép thao tác picking khi đơn đã chuyển sang trạng thái lấy hàng.
        if (status == SalesOrderStatus.PICKING) {
            return;
        }
        
        throw new AppException(ErrorCode.BAD_REQUEST, 
            "Không thể thao tác picking khi đơn ở trạng thái: " + status);
    }

    // Parse chuỗi trạng thái về enum PickingItemStatus.
    private static PickingItemStatus parsePickingStatus(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase();
        if (Set.of("COMPLETED", "COMPLETE", "DONE").contains(normalized)) {
            return PickingItemStatus.PICKED;
        }
        try {
            return PickingItemStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái picking không hợp lệ: " + raw);
        }
    }

    private static PickingItemStatus parseOptionalPickingStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parsePickingStatus(raw);
    }

    private static SalesOrderStatus parseOptionalSalesOrderStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SalesOrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái đơn xuất không hợp lệ: " + raw);
        }
    }

    // Kiểm tra tính hợp lệ của qtyToPick, qtyPicked theo trạng thái.
    private static void validateQuantities(Integer qtyToPick, Integer qtyPicked, PickingItemStatus status) {
        if (qtyToPick == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "qtyToPick là bắt buộc");
        }
        int picked = qtyPicked == null ? 0 : qtyPicked;
        if (qtyToPick <= 0 || picked < 0 || picked > qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng pick không hợp lệ");
        }
        if (status == PickingItemStatus.PICKED && picked != qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái PICKED yêu cầu qtyPicked bằng qtyToPick");
        }
    }

    private void validateAllocationAgainstOrderedQty(SalesOrderItem line,
            Integer requestedQtyToPick, Integer requestedQtyPicked, UUID currentPickingId) {
        int orderedQty = line.getOrderedQty() == null ? 0 : line.getOrderedQty();
        int qtyToPick = requestedQtyToPick == null ? 0 : requestedQtyToPick;
        int qtyPicked = requestedQtyPicked == null ? 0 : requestedQtyPicked;

        int allocatedByOtherPicks = 0;
        int pickedByOtherPicks = 0;
        for (PickingItem pick : pickingItemRepository.findBySoItem_Id(line.getId())) {
            if (currentPickingId != null && currentPickingId.equals(pick.getId())) {
                continue;
            }

            allocatedByOtherPicks += pick.getQtyToPick() == null ? 0 : pick.getQtyToPick();
            pickedByOtherPicks += pick.getQtyPicked() == null ? 0 : pick.getQtyPicked();
        }

        int totalAllocated = allocatedByOtherPicks + qtyToPick;
        if (totalAllocated > orderedQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tổng qtyToPick cho line " + line.getLineNumber()
                            + " vượt orderedQty (" + totalAllocated + "/" + orderedQty + ")");
        }

        int totalPicked = pickedByOtherPicks + qtyPicked;
        if (totalPicked > orderedQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tổng qtyPicked cho line " + line.getLineNumber()
                            + " vượt orderedQty (" + totalPicked + "/" + orderedQty + ")");
        }
    }

    private void validatePickedAgainstOrderedQty(SalesOrderItem line, Integer requestedQtyPicked, UUID currentPickingId) {
        int orderedQty = line.getOrderedQty() == null ? 0 : line.getOrderedQty();
        int qtyPicked = requestedQtyPicked == null ? 0 : requestedQtyPicked;

        int pickedByOtherPicks = 0;
        for (PickingItem pick : pickingItemRepository.findBySoItem_Id(line.getId())) {
            if (currentPickingId != null && currentPickingId.equals(pick.getId())) {
                continue;
            }

            pickedByOtherPicks += pick.getQtyPicked() == null ? 0 : pick.getQtyPicked();
        }

        int totalPicked = pickedByOtherPicks + qtyPicked;
        if (totalPicked > orderedQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tổng qtyPicked cho line " + line.getLineNumber()
                            + " vượt orderedQty (" + totalPicked + "/" + orderedQty + ")");
        }
    }

    // Tính tồn khả dụng từ dữ liệu stock.
    private static int availableQty(StockLevelResponse r) {
        if (r.qtyAvailable() != null) {
            return r.qtyAvailable();
        }
        int on = r.qtyOnHand() == null ? 0 : r.qtyOnHand();
        int res = r.qtyReserved() == null ? 0 : r.qtyReserved();
        return Math.max(0, on - res);
    }

    // Chuẩn hóa lot number về chuỗi không null.
    private static String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private static StockReserveCommand reserveCommand(SalesOrder so, UUID locationId, UUID productId,
            String lotNumber, int reservedDelta, UUID pickingItemId, String idempotencyKey) {
        return new StockReserveCommand(
                so.getWarehouseId(),
                locationId,
                productId,
                lotNumber,
                reservedDelta,
                idempotencyKey,
                "PICKING_ITEM",
                pickingItemId);
    }

    private static String reserveMutationKey(UUID pickingItemId, String action,
            UUID locationId, UUID productId, String lotNumber, int qty) {
        return "PICKING_ITEM:" + pickingItemId + ":" + action
                + ":" + locationId + ":" + productId + ":" + normalizeLot(lotNumber) + ":" + qty;
    }

    private String pickingEntityName(PickingItem item) {
        String salesOrderNumber = item.getSoItem() == null || item.getSoItem().getSalesOrder() == null
                ? null
                : item.getSoItem().getSalesOrder().getSoNumber();
        return (salesOrderNumber == null ? "SO" : salesOrderNumber)
                + " / product=" + item.getProductId();
    }

    private Map<String, Object> pickingMetadata(PickingItem item) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("soItemId", item.getSoItem() == null ? null : item.getSoItem().getId());
        metadata.put("salesOrderId", item.getSoItem() == null || item.getSoItem().getSalesOrder() == null
                ? null
                : item.getSoItem().getSalesOrder().getId());
        metadata.put("productId", item.getProductId());
        metadata.put("locationId", item.getLocationId());
        metadata.put("lotNumber", normalizeLot(item.getLotNumber()));
        metadata.put("qtyToPick", item.getQtyToPick());
        metadata.put("qtyPicked", item.getQtyPicked());
        metadata.put("status", item.getStatus() == null ? null : item.getStatus().name());
        return metadata;
    }

    private void notifyPickingAssigned(PickingItem item) {
        if (item.getAssigneeId() == null) {
            return;
        }
        String salesOrderNumber = item.getSoItem() == null || item.getSoItem().getSalesOrder() == null
                ? "đơn xuất"
                : item.getSoItem().getSalesOrder().getSoNumber();
        String productLabel = pickingProductLabel(item);
        String locationLabel = pickingLocationLabel(item);
        String quantityLabel = item.getQtyToPick() == null ? "" : " · SL " + item.getQtyToPick();
        notificationService.create(new CreateNotificationCommand(
                item.getAssigneeId(),
                NotificationType.PICKING_ASSIGNED,
                NotificationSeverity.INFO,
                "Bạn được giao nhiệm vụ picking",
                "Đơn " + salesOrderNumber + " · " + productLabel + quantityLabel + " · Vị trí " + locationLabel,
                "PICKING_ITEM",
                item.getId()));
    }

    private String pickingProductLabel(PickingItem item) {
        try {
            ProductResponse product = productService.findById(item.getProductId());
            if (product == null) {
                return "Sản phẩm chưa rõ";
            }
            if (product.sku() != null && !product.sku().isBlank() && product.name() != null && !product.name().isBlank()) {
                return product.name() + " (" + product.sku() + ")";
            }
            if (product.name() != null && !product.name().isBlank()) {
                return product.name();
            }
            if (product.sku() != null && !product.sku().isBlank()) {
                return product.sku();
            }
        } catch (Exception e) {
            log.warn("Failed to build picking notification product label productId={}", item.getProductId(), e);
        }
        return "Sản phẩm chưa rõ";
    }

    private String pickingLocationLabel(PickingItem item) {
        try {
            LocationResponse location = locationService.findById(item.getLocationId());
            if (location != null && location.code() != null && !location.code().isBlank()) {
                return location.code();
            }
        } catch (Exception e) {
            log.warn("Failed to build picking notification location label locationId={}", item.getLocationId(), e);
        }
        return "chưa rõ";
    }

    private void assertAssigneeCanWorkInWarehouse(UUID assigneeId, UUID warehouseId) {
        if (assigneeId == null) {
            return;
        }
        if (!userRepository.existsActiveWarehouseStaffInWarehouse(assigneeId, warehouseId)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Nhân viên chưa được phân quyền vào kho của đơn xuất này");
        }
    }

    // Lấy chi tiết picking item đầy đủ dữ liệu cho giao diện picker.
    public PickingItemDetailResponse findDetailForPicker(UUID id) {
        return findDetailForPicker(id, null, true);
    }

    public PickingItemDetailResponse findDetailForPicker(UUID id, UUID actorId, boolean canBypassAssignment) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        assertPickingAssignmentAllowed(item, actorId, canBypassAssignment);
        SalesOrderItem soItem = item.getSoItem();
        SalesOrder so = soItem.getSalesOrder();

        // Fetch product details
        ProductResponse productData;
        try {
            productData = productService.findById(item.getProductId());
            if (productData == null) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm");
            }
        } catch (Exception e) {
            log.warn("Failed to load product details for productId={}", item.getProductId(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lấy thông tin sản phẩm");
        }

        // Fetch location details
        LocationResponse locationData;
        try {
            locationData = locationService.findById(item.getLocationId());
            if (locationData == null) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí kho");
            }
        } catch (Exception e) {
            log.warn("Failed to load location details for locationId={}", item.getLocationId(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lấy thông tin vị trí kho");
        }

        Warehouse warehouse = warehouseRepository.findById(so.getWarehouseId()).orElse(null);

        // Get current available qty at this location for this product+lot
        // Tối ưu: sử dụng getSingleStockRowIfExists thay vì listAllStocksForProduct để
        // giảm API calls
        int qtyAvailable = item.getQtyToPick();
        try {
            PagedResponse<StockLevelResponse> stockPage = stockLevelService.findAll(Pageable.unpaged(), so.getWarehouseId(), item.getLocationId(), item.getProductId());
            if (stockPage.content() != null && !stockPage.content().isEmpty()) {
                StockLevelResponse stock = stockPage.content().stream().filter(s -> normalizeLot(s.lotNumber()).equals(normalizeLot(item.getLotNumber()))).findFirst().orElse(null);
                if (stock != null) {
                    qtyAvailable = availableQty(stock);
                }
            }
        } catch (Exception e) {
            // Keep API resilient for picker UI.
            log.warn("Failed to query stock row for pickingItemId={}: {}", item.getId(), e.getMessage());
            qtyAvailable = item.getQtyToPick();
        }

        return new PickingItemDetailResponse(
                item.getId(),
                item.getSoItem().getId(),
                so.getSoNumber(),
                so.getWarehouseId(),
                warehouse == null ? null : warehouse.getCode(),
                warehouse == null ? null : warehouse.getName(),

                productData.id(),
                productData.sku(),
                productData.name(),
                soItem.getProductSku(),
                productData.barcodeEan13(),
                productData.categoryName(),
                productData.baseUnit(),

                locationData.id(),
                locationData.code(),
                locationData.code(), // name matches code
                locationData.zone(),
                locationData.aisle(),
                locationData.rack(), // shelf maps to rack
                locationData.bin(),  // position maps to bin

                item.getLotNumber(),
                item.getQtyToPick(),
                item.getQtyPicked(),
                qtyAvailable,

                item.getStatus().name(),
                item.getPickSequence());
    }

    private void assertPickingAssignmentAllowed(PickingItem item, UUID actorId, boolean canBypassAssignment) {
        if (canBypassAssignment) {
            return;
        }
        if (actorId == null || item.getAssigneeId() == null || !actorId.equals(item.getAssigneeId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn chỉ được thao tác nhiệm vụ picking được phân công cho bạn");
        }
    }
}
