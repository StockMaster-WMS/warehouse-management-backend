package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.response.ProductSummaryResponse;
import com.product_service.service.ProductService;
import com.warehouse_service.dto.request.CreateCycleCountRequest;
import com.warehouse_service.dto.request.RecordCountRequest;
import com.warehouse_service.dto.response.CycleCountResponse;
import com.warehouse_service.entity.CycleCount;
import com.warehouse_service.entity.CycleCount.CycleCountStatus;
import com.warehouse_service.entity.CycleCountItem;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.CycleCountItemRepository;
import com.warehouse_service.repository.CycleCountRepository;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final CycleCountItemRepository cycleCountItemRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockLevelService stockLevelService;
    private final ProductService productService;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;

    // ─── Queries ─────────────────────────────────────────────────────────────

    /**
     * Danh sách phân trang + filter theo keyword, status, warehouseId.
     */
    public PagedResponse<CycleCountResponse> getAll(Pageable pageable, String keyword,
                                                     String status, UUID warehouseId) {
        Specification<CycleCount> spec = buildSpec(keyword, status, warehouseId);
        Page<CycleCount> page = cycleCountRepository.findAll(spec, pageable);
        List<CycleCountResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PagedResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Chi tiết một đợt kiểm kê.
     */
    public CycleCountResponse getById(UUID id) {
        return toResponse(getEntity(id));
    }

    // ─── Commands ────────────────────────────────────────────────────────────

    /**
     * Tạo đợt kiểm kê mới.
     * Sinh items ngay dựa trên scope hoặc manual items.
     */
    @Transactional
    public CycleCountResponse create(CreateCycleCountRequest request, UUID creatorId) {
        // Validate scope vs items
        boolean hasScope = StringUtils.hasText(request.scope());
        boolean hasItems = request.items() != null && !request.items().isEmpty();

        if (hasScope && hasItems) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không được cung cấp cả scope và items. Chọn một trong hai.");
        }
        if (!hasScope && !hasItems) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Phải cung cấp scope hoặc items để kiểm kê.");
        }

        // Validate warehouse exists
        warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));

        CycleCount count = CycleCount.builder()
                .warehouseId(request.warehouseId())
                .description(request.description())
                .scope(hasScope ? request.scope().toUpperCase() : "MANUAL")
                .scopeValue(request.scopeValue())
                .scheduledAt(request.scheduledAt() != null ? request.scheduledAt() : OffsetDateTime.now())
                .status(CycleCountStatus.PENDING)
                .createdBy(creatorId)
                .build();

        CycleCount saved = cycleCountRepository.save(count);

        // Resolve stock levels and create items
        List<StockLevel> stockLevels;
        if (hasScope) {
            stockLevels = resolveStockLevelsByScope(request.warehouseId(),
                    request.scope(), request.scopeValue());
            if (stockLevels.isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Không tìm thấy tồn kho nào theo phạm vi: " + request.scope());
            }
        } else {
            stockLevels = request.items().stream()
                    .map(req -> stockLevelRepository.findByLocationIdAndProductIdAndLotNumber(
                            req.locationId(), req.productId(),
                            req.lotNumber() != null ? req.lotNumber() : "")
                            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                                    "Không tìm thấy tồn kho: product=" + req.productId()
                                            + ", location=" + req.locationId())))
                    .collect(Collectors.toList());
        }

        List<CycleCountItem> items = stockLevels.stream().map(sl ->
                CycleCountItem.builder()
                        .cycleCount(saved)
                        .productId(sl.getProductId())
                        .locationId(sl.getLocation().getId())
                        .lotNumber(sl.getLotNumber())
                        .systemQty(sl.getQtyOnHand())
                        .status(CycleCountItem.ItemStatus.PENDING)
                        .build()
        ).collect(Collectors.toList());

        cycleCountItemRepository.saveAll(items);
        saved.setItems(items);

        return toResponse(saved);
    }

    /**
     * Bắt đầu kiểm kê: PENDING → IN_PROGRESS.
     */
    @Transactional
    public CycleCountResponse startCounting(UUID id) {
        CycleCount count = getEntity(id);
        assertStatus(count, CycleCountStatus.PENDING, "Chỉ bắt đầu khi đợt đang PENDING");

        count.setStatus(CycleCountStatus.IN_PROGRESS);
        count.setStartedAt(OffsetDateTime.now());

        return toResponse(cycleCountRepository.save(count));
    }

    /**
     * Ghi nhận kết quả kiểm đếm (batch).
     * Chỉ cho phép khi IN_PROGRESS.
     */
    @Transactional
    public CycleCountResponse recordCount(UUID countId, RecordCountRequest request) {
        CycleCount count = getEntity(countId);
        assertStatus(count, CycleCountStatus.IN_PROGRESS, "Chỉ ghi nhận khi đang IN_PROGRESS");

        if (request.results() == null || request.results().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh sách kết quả không được rỗng");
        }

        for (RecordCountRequest.ItemResult result : request.results()) {
            CycleCountItem item = count.getItems().stream()
                    .filter(i -> i.getProductId().equals(result.productId())
                            && i.getLocationId().equals(result.locationId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy dòng kiểm kê: product=" + result.productId()
                                    + ", location=" + result.locationId()));

            item.setCountedQty(result.actualQty());
            item.setDiscrepancy(result.actualQty() - item.getSystemQty());
            item.setNotes(result.notes());
            item.setStatus(CycleCountItem.ItemStatus.COUNTED);

            cycleCountItemRepository.save(item);
        }

        return toResponse(count);
    }

    /**
     * Nộp kết quả kiểm kê: IN_PROGRESS → COMPLETED (chờ duyệt).
     * Validate: tất cả items phải đã được đếm.
     */
    @Transactional
    public CycleCountResponse submitForReview(UUID id) {
        CycleCount count = getEntity(id);
        assertStatus(count, CycleCountStatus.IN_PROGRESS, "Chỉ nộp khi đang IN_PROGRESS");

        // Validate: tất cả items phải đã COUNTED
        long pendingCount = count.getItems().stream()
                .filter(i -> i.getStatus() == CycleCountItem.ItemStatus.PENDING)
                .count();
        if (pendingCount > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Còn " + pendingCount + " dòng chưa được đếm. Vui lòng hoàn tất trước khi nộp.");
        }

        count.setStatus(CycleCountStatus.COMPLETED);
        return toResponse(cycleCountRepository.save(count));
    }

    /**
     * Duyệt kiểm kê và điều chỉnh tồn kho: COMPLETED → APPROVED.
     * Tạo stock adjustment cho mỗi item có chênh lệch != 0.
     */
    @Transactional
    public CycleCountResponse approveAndAdjust(UUID id, UUID approverId) {
        CycleCount count = getEntity(id);
        assertStatus(count, CycleCountStatus.COMPLETED, "Chỉ duyệt khi đợt đang COMPLETED (chờ duyệt)");

        for (CycleCountItem item : count.getItems()) {
            if (item.getStatus() != CycleCountItem.ItemStatus.COUNTED) {
                continue; // Items đã adjusted hoặc chưa đếm (shouldn't happen sau submit)
            }

            if (item.getDiscrepancy() != null && item.getDiscrepancy() != 0) {
                StockAdjustCommand cmd = new StockAdjustCommand(
                        count.getWarehouseId(),
                        item.getLocationId(),
                        item.getProductId(),
                        item.getLotNumber(),
                        item.getDiscrepancy(),
                        "CYCLE_COUNT:" + count.getId() + ":" + item.getId(),
                        "CYCLE_COUNT",
                        count.getId()
                );
                stockLevelService.adjust(cmd);
            }
            item.setStatus(CycleCountItem.ItemStatus.ADJUSTED);
            cycleCountItemRepository.save(item);
        }

        count.setStatus(CycleCountStatus.APPROVED);
        count.setCompletedAt(OffsetDateTime.now());
        count.setApprovedBy(approverId);

        return toResponse(cycleCountRepository.save(count));
    }

    /**
     * Huỷ đợt kiểm kê: PENDING/IN_PROGRESS → CANCELLED.
     */
    @Transactional
    public CycleCountResponse cancel(UUID id) {
        CycleCount count = getEntity(id);

        if (count.getStatus() != CycleCountStatus.PENDING
                && count.getStatus() != CycleCountStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ huỷ khi đợt đang PENDING hoặc IN_PROGRESS (hiện tại: " + count.getStatus() + ")");
        }

        count.setStatus(CycleCountStatus.CANCELLED);
        return toResponse(cycleCountRepository.save(count));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CycleCount getEntity(UUID id) {
        return cycleCountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt kiểm kê"));
    }

    private void assertStatus(CycleCount count, CycleCountStatus expected, String message) {
        if (count.getStatus() != expected) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    message + " (hiện tại: " + count.getStatus() + ")");
        }
    }

    private Specification<CycleCount> buildSpec(String keyword, String status, UUID warehouseId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("countNumber")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                try {
                    CycleCountStatus s = CycleCountStatus.valueOf(status.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), s));
                } catch (IllegalArgumentException ignored) {
                    // Bỏ qua status không hợp lệ
                }
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Resolve stock levels based on scope.
     */
    private List<StockLevel> resolveStockLevelsByScope(UUID warehouseId, String scope, String scopeValue) {
        return switch (scope.toUpperCase()) {
            case "WAREHOUSE" -> stockLevelRepository.findByWarehouseId(warehouseId);
            case "ZONE" -> {
                if (!StringUtils.hasText(scopeValue)) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "scopeValue (tên zone) là bắt buộc cho phạm vi ZONE");
                }
                yield stockLevelRepository.findByWarehouseIdAndZone(warehouseId, scopeValue);
            }
            case "LOCATION" -> {
                if (!StringUtils.hasText(scopeValue)) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "scopeValue (location ID) là bắt buộc cho phạm vi LOCATION");
                }
                yield stockLevelRepository.findByLocationIdWithDetails(UUID.fromString(scopeValue));
            }
            case "PRODUCT" -> {
                if (!StringUtils.hasText(scopeValue)) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "scopeValue (product ID) là bắt buộc cho phạm vi PRODUCT");
                }
                yield stockLevelRepository.findByWarehouseIdAndProductIdWithDetails(
                        warehouseId, UUID.fromString(scopeValue));
            }
            default -> throw new AppException(ErrorCode.BAD_REQUEST,
                    "Phạm vi không hợp lệ: " + scope + ". Chọn WAREHOUSE, ZONE, LOCATION hoặc PRODUCT.");
        };
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private CycleCountResponse toResponse(CycleCount count) {
        // Warehouse name
        String warehouseName = null;
        if (count.getWarehouseId() != null) {
            warehouseName = warehouseRepository.findById(count.getWarehouseId())
                    .map(Warehouse::getName).orElse(null);
        }

        // Lines
        List<CycleCountResponse.LineResponse> lines;
        if (count.getItems() == null || count.getItems().isEmpty()) {
            lines = List.of();
        } else {
            // Batch-fetch product & location info
            Set<UUID> productIds = count.getItems().stream()
                    .map(CycleCountItem::getProductId).collect(Collectors.toSet());
            Set<UUID> locationIds = count.getItems().stream()
                    .map(CycleCountItem::getLocationId).collect(Collectors.toSet());

            Map<UUID, ProductSummaryResponse> productMap = loadProducts(productIds);
            Map<UUID, Location> locationMap = locationRepository.findAllById(locationIds)
                    .stream().collect(Collectors.toMap(Location::getId, l -> l));

            lines = count.getItems().stream().map(item -> {
                ProductSummaryResponse prod = productMap.get(item.getProductId());
                Location loc = locationMap.get(item.getLocationId());
                return new CycleCountResponse.LineResponse(
                        item.getId(),
                        item.getProductId(),
                        prod != null ? prod.name() : "N/A",
                        prod != null ? prod.sku() : "N/A",
                        item.getLocationId(),
                        loc != null ? loc.getCode() : "N/A",
                        item.getLotNumber(),
                        item.getSystemQty(),
                        item.getCountedQty(),
                        item.getDiscrepancy(),
                        item.getStatus().name(),
                        item.getNotes()
                );
            }).collect(Collectors.toList());
        }

        return new CycleCountResponse(
                count.getId(),
                count.getCountNumber(),
                count.getWarehouseId(),
                warehouseName,
                count.getStatus().name(),
                count.getDescription(),
                count.getScope(),
                count.getScopeValue(),
                count.getScheduledAt(),
                count.getStartedAt(),
                count.getCompletedAt(),
                count.getCreatedBy(),
                count.getApprovedBy(),
                count.getCreatedAt(),
                lines
        );
    }

    private Map<UUID, ProductSummaryResponse> loadProducts(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            return productService.findSummariesByIds(new ArrayList<>(ids))
                    .stream().collect(Collectors.toMap(ProductSummaryResponse::id, p -> p));
        } catch (Exception e) {
            log.warn("Failed to load product summaries: {}", e.getMessage());
            return Map.of();
        }
    }
}
