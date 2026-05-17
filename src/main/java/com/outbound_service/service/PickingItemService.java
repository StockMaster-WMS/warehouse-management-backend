package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockReserveCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.service.ProductService;
import com.product_service.dto.response.ProductResponse;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PickingItemService {

    private final PickingItemRepository pickingItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemMapper pickingItemMapper;
    private final StockLevelService stockLevelService;
    private final ProductService productService;
    private final LocationService locationService;
    private final AuditLogService auditLogService;

    // Lấy danh sách picking item có phân trang và bộ lọc.
    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId,
            UUID locationId, String status) {
        Specification<PickingItem> spec = PickingItemSpecification.hasSoItemId(soItemId)
                .and(PickingItemSpecification.hasProductId(productId))
                .and(PickingItemSpecification.hasLocationId(locationId))
                .and(PickingItemSpecification.hasStatus(status));
        Page<PickingItem> page = pickingItemRepository.findAll(spec, pageable);
        Map<UUID, ProductResponse> productCache = new HashMap<>();
        Map<UUID, LocationResponse> locationCache = new HashMap<>();
        List<PickingItemResponse> rows = page.getContent().stream()
                .map(pickingItemMapper::toResponse)
                .map(row -> enrichListRow(row, productCache, locationCache))
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
            Map<UUID, ProductResponse> productCache,
            Map<UUID, LocationResponse> locationCache) {
        ProductResponse product = productCache.computeIfAbsent(
                row.productId(), this::loadProductSafe);
        LocationResponse location = locationCache.computeIfAbsent(
                row.locationId(), this::loadLocationSafe);

        String productSku = row.productSku();
        if ((productSku == null || productSku.isBlank()) && product != null) {
            productSku = product.sku();
        }

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
                product == null ? null : product.name(),
                product == null ? null : product.barcodeEan13(),
                location == null ? null : location.code(),
                location == null ? null : location.code(),
                row.assigneeId());
    }

    // Tải thông tin sản phẩm theo hướng an toàn, không làm hỏng luồng danh sách.
    private ProductResponse loadProductSafe(UUID productId) {
        try {
            return productService.findById(productId);
        } catch (Exception e) {
            // Keep list resilient when product details cannot be loaded.
            log.warn("Failed to load product details for productId={}: {}", productId, e.getMessage());
            return null;
        }
    }

    // Tải thông tin vị trí theo hướng an toàn, không làm hỏng luồng danh sách.
    private LocationResponse loadLocationSafe(UUID locationId) {
        try {
            return locationService.findById(locationId);
        } catch (Exception e) {
            // Keep list resilient when warehouse location lookup fails.
            log.warn("Failed to load location details for locationId={}: {}", locationId, e.getMessage());
            return null;
        }
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

        validateQuantities(existing.getQtyToPick(), existing.getQtyPicked(), newStatus);
        validateAllocationAgainstOrderedQty(line, existing.getQtyToPick(), existing.getQtyPicked(), existing.getId());

        String newLot = normalizeLot(existing.getLotNumber());
        boolean allocChanged = !oldLoc.equals(existing.getLocationId())
                || !oldProd.equals(existing.getProductId())
                || oldQtyToPick != existing.getQtyToPick()
                || !oldLot.equals(newLot);


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

        // Logic trọng tâm cho Mobile: Khi hoàn tất lấy hàng (PICKED), trừ tồn thực tế và giải phóng giữ chỗ
        if (saved.getStatus() == PickingItemStatus.PICKED && (before.status() == null || !before.status().equals("PICKED"))) {
            try {
                // 1. Trừ tồn tay (OnHand)
                stockLevelService.adjust(new com.common.api.stock.StockAdjustCommand(
                        so.getWarehouseId(),
                        saved.getLocationId(),
                        saved.getProductId(),
                        org.springframework.util.StringUtils.hasText(saved.getLotNumber()) ? saved.getLotNumber() : "",
                        -saved.getQtyToPick(),
                        "PICKING_ITEM:" + saved.getId() + ":COMPLETE_PICK",
                        "PICKING_ITEM",
                        saved.getId()
                ));

                // 2. Giải phóng giữ chỗ (Reserved)
                stockLevelService.adjustReserved(new com.common.api.stock.StockReserveCommand(
                        so.getWarehouseId(),
                        saved.getLocationId(),
                        saved.getProductId(),
                        org.springframework.util.StringUtils.hasText(saved.getLotNumber()) ? saved.getLotNumber() : "",
                        -saved.getQtyToPick(),
                        "PICKING_ITEM:" + saved.getId() + ":COMPLETE_RELEASE",
                        "PICKING_ITEM",
                        saved.getId()
                ));
            } catch (Exception e) {
                log.error("Lỗi cập nhật tồn kho khi hoàn tất picking: {}", e.getMessage());
            }
        }

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

        item.setAssigneeId(assigneeId);
        PickingItem saved = pickingItemRepository.save(item);
        
        PickingItemResponse after = pickingItemMapper.toResponse(saved);
        auditLogService.record("PICKING", "ASSIGN", "Phân công nhiệm vụ picking",
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), before, after,
                null, pickingMetadata(saved));
        return after;
    }

    @Transactional
    public PickingItemResponse reportException(UUID id, String reason) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
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

        auditLogService.record("PICKING", "EXCEPTION", "Báo lỗi lấy hàng: " + reason,
                "PICKING_ITEM", saved.getId(), pickingEntityName(saved), before, after,
                null, meta);
        return after;
    }

    @Transactional
    public PickingItemResponse completeMobile(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
        
        if (item.getStatus() == PickingItemStatus.PICKED) {
            return pickingItemMapper.toResponse(item); // Đã hoàn tất
        }

        UpdatePickingItemRequest request = new UpdatePickingItemRequest(
            item.getSoItem().getId(),
            item.getProductId(),
            item.getLocationId(),
            item.getQtyToPick(),
            item.getQtyToPick(), // Hoàn tất nên qtyPicked = qtyToPick
            "PICKED",
            item.getPickSequence(),
            item.getLotNumber()
        );

        // Reuse the update logic which handles the physical stock deduction and reserve releasing
        return update(id, request);
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
        try {
            return PickingItemStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái picking không hợp lệ: " + raw);
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

    // Lấy chi tiết picking item đầy đủ dữ liệu cho giao diện picker.
    public PickingItemDetailResponse findDetailForPicker(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

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
}
