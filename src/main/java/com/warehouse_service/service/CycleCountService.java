package com.warehouse_service.service;

import com.auth_service.entity.UserAccount;
import com.auth_service.repository.UserRepository;
import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.notification.CreateNotificationCommand;
import com.common.notification.NotificationService;
import com.common.notification.NotificationSeverity;
import com.common.notification.NotificationType;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PagedResponse<CycleCountResponse> getAll(Pageable pageable, String keyword, String status, UUID warehouseId) {
        return getAll(pageable, keyword, status, warehouseId, null, null, false, false);
    }

    public PagedResponse<CycleCountResponse> getAll(Pageable pageable, String keyword, String status, UUID warehouseId,
            UUID actorId, Collection<UUID> visibleWarehouseIds, boolean staffOnly, boolean reportOnly) {
        Specification<CycleCount> spec = buildSpec(keyword, status, warehouseId, actorId,
                visibleWarehouseIds, staffOnly, reportOnly);
        Page<CycleCount> page = cycleCountRepository.findAll(spec, pageable);
        List<UUID> countIds = page.getContent().stream().map(CycleCount::getId).toList();
        Map<UUID, CycleCountItemRepository.CycleCountItemStatsView> statsByCountId = countIds.isEmpty()
                ? Map.of()
                : cycleCountItemRepository.summarizeByCycleCountIds(countIds).stream()
                        .collect(Collectors.toMap(
                                CycleCountItemRepository.CycleCountItemStatsView::getCycleCountId,
                                Function.identity()));
        Map<UUID, String> warehouseNamesById = page.getContent().stream()
                .map(CycleCount::getWarehouseId)
                .filter(id -> id != null)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ids -> ids.isEmpty()
                                ? Map.<UUID, String>of()
                                : warehouseRepository.findAllById(ids).stream()
                                        .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName))));
        List<CycleCountResponse> content = page.getContent().stream()
                .map(count -> toSummaryResponse(count, statsByCountId.get(count.getId()), warehouseNamesById))
                .toList();
        return new PagedResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public CycleCountResponse getById(UUID id) {
        return toResponse(getEntity(id));
    }

    public CycleCountResponse getById(UUID id, UUID actorId, Collection<UUID> visibleWarehouseIds,
            boolean staffOnly, boolean reportOnly) {
        CycleCount count = getEntity(id);
        assertVisible(count, actorId, visibleWarehouseIds, staffOnly, reportOnly);
        return toResponse(count);
    }

    @Transactional
    public CycleCountResponse create(CreateCycleCountRequest request, UUID creatorId) {
        boolean hasScope = StringUtils.hasText(request.scope());
        boolean hasItems = request.items() != null && !request.items().isEmpty();

        if (hasScope && hasItems) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được cung cấp cả scope và items. Chọn một trong hai.");
        }
        if (!hasScope && !hasItems) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phải cung cấp scope hoặc items để kiểm kê.");
        }

        warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
        UserAccount assignee = request.assignedTo() == null ? null : requireActiveStaff(request.assignedTo());

        CycleCount count = CycleCount.builder()
                .warehouseId(request.warehouseId())
                .description(request.description())
                .scope(hasScope ? request.scope().trim().toUpperCase() : "MANUAL")
                .scopeValue(request.scopeValue())
                .scheduledAt(request.scheduledAt() != null ? request.scheduledAt() : OffsetDateTime.now())
                .status(CycleCountStatus.PENDING)
                .createdBy(creatorId)
                .assignedTo(assignee == null ? null : assignee.getId())
                .build();

        CycleCount saved = cycleCountRepository.save(count);
        List<StockLevelRepository.CycleCountStockSnapshotView> stockLevels = hasScope
                ? resolveStockSnapshotsByScope(request.warehouseId(), request.scope(), request.scopeValue())
                : resolveManualStockSnapshots(request);
        if (stockLevels.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không tìm thấy tồn kho nào theo phạm vi kiểm kê.");
        }

        List<CycleCountItem> items = stockLevels.stream()
                .map(sl -> CycleCountItem.builder()
                        .cycleCount(saved)
                        .productId(sl.getProductId())
                        .locationId(sl.getLocationId())
                        .lotNumber(normalizeLot(sl.getLotNumber()))
                        .systemQty(sl.getQtyOnHand())
                        .status(CycleCountItem.ItemStatus.PENDING)
                        .build())
                .toList();
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có dòng tồn kho thuộc đúng kho cần kiểm kê.");
        }
        cycleCountItemRepository.saveAll(items);
        saved.setItems(items);

        CycleCountResponse response = toCreateResponse(saved, items);
        auditLogService.record("CYCLE_COUNT", "CREATE", "Tạo phiếu kiểm kê",
                "CYCLE_COUNT", saved.getId(), displayCycleCount(saved), null, response,
                null, cycleCountMetadata(saved));
        notifyCycleCountCreated(saved, assignee);
        return response;
    }

    @Transactional
    public CycleCountResponse startCounting(UUID id) {
        return startCounting(id, null, null, false);
    }

    @Transactional
    public CycleCountResponse startCounting(UUID id, UUID actorId, Collection<UUID> visibleWarehouseIds, boolean staffOnly) {
        CycleCount count = getEntityForUpdate(id);
        assertVisible(count, actorId, visibleWarehouseIds, staffOnly, false);
        if (count.getStatus() != CycleCountStatus.PENDING && count.getStatus() != CycleCountStatus.RECOUNT_REQUIRED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ bắt đầu khi phiếu đang PENDING hoặc RECOUNT_REQUIRED (hiện tại: " + count.getStatus() + ")");
        }

        CycleCountResponse before = toResponse(count);
        count.setStatus(CycleCountStatus.IN_PROGRESS);
        count.setStartedAt(OffsetDateTime.now());
        count.setRejectedAt(null);
        count.setRejectedBy(null);
        count.setRejectionReason(null);
        CycleCount saved = cycleCountRepository.save(count);
        CycleCountResponse after = toResponse(saved);
        auditLogService.record("CYCLE_COUNT", "START", "Bắt đầu kiểm kê",
                "CYCLE_COUNT", saved.getId(), displayCycleCount(saved), before, after, null, cycleCountMetadata(saved));
        return after;
    }

    @Transactional
    public CycleCountResponse recordCount(UUID countId, RecordCountRequest request) {
        return recordCount(countId, request, null, null, false);
    }

    @Transactional
    public CycleCountResponse recordCount(UUID countId, RecordCountRequest request,
            UUID actorId, Collection<UUID> visibleWarehouseIds, boolean staffOnly) {
        CycleCount count = getEntityForUpdate(countId);
        assertVisible(count, actorId, visibleWarehouseIds, staffOnly, false);
        assertStatus(count, CycleCountStatus.IN_PROGRESS, "Chỉ ghi nhận khi đang IN_PROGRESS");

        if (request.results() == null || request.results().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh sách kết quả không được rỗng");
        }

        CycleCountResponse before = toResponse(count);
        for (RecordCountRequest.ItemResult result : request.results()) {
            if (result.actualQty() == null || result.actualQty() < 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng thực tế không được trống hoặc âm");
            }

            CycleCountItem item = findItem(count, result);
            item.setCountedQty(result.actualQty());
            item.setDiscrepancy(result.actualQty() - safeQty(item.getSystemQty()));
            item.setNotes(result.notes());
            item.setStatus(CycleCountItem.ItemStatus.COUNTED);
            cycleCountItemRepository.save(item);
        }

        CycleCountResponse after = toResponse(count);
        auditLogService.record("CYCLE_COUNT", "RECORD", "Nhập số lượng thực tế kiểm kê",
                "CYCLE_COUNT", count.getId(), displayCycleCount(count), before, after, null, cycleCountMetadata(count));
        return after;
    }

    @Transactional
    public CycleCountResponse submitForReview(UUID id) {
        return submitForReview(id, null, null, false);
    }

    @Transactional
    public CycleCountResponse submitForReview(UUID id, UUID actorId, Collection<UUID> visibleWarehouseIds, boolean staffOnly) {
        CycleCount count = getEntityForUpdate(id);
        assertVisible(count, actorId, visibleWarehouseIds, staffOnly, false);
        assertStatus(count, CycleCountStatus.IN_PROGRESS, "Chỉ gửi duyệt khi đang IN_PROGRESS");

        long pendingCount = count.getItems().stream()
                .filter(i -> i.getStatus() == CycleCountItem.ItemStatus.PENDING)
                .count();
        if (pendingCount > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Còn " + pendingCount + " dòng chưa được đếm. Vui lòng hoàn tất trước khi gửi duyệt.");
        }

        CycleCountResponse before = toResponse(count);
        count.setStatus(CycleCountStatus.PENDING_REVIEW);
        count.setSubmittedAt(OffsetDateTime.now());
        CycleCount saved = cycleCountRepository.save(count);
        CycleCountResponse after = toResponse(saved);
        auditLogService.record("CYCLE_COUNT", "SUBMIT", "Gửi kết quả kiểm kê chờ duyệt",
                "CYCLE_COUNT", saved.getId(), displayCycleCount(saved), before, after, null, cycleCountMetadata(saved));
        notifySubmitted(saved);
        notifyStockDiscrepancies(saved);
        return after;
    }

    @Transactional
    public CycleCountResponse approveAndAdjust(UUID id, UUID approverId) {
        return approveAndAdjust(id, approverId, null);
    }

    @Transactional
    public CycleCountResponse approveAndAdjust(UUID id, UUID approverId, Collection<UUID> visibleWarehouseIds) {
        CycleCount count = getEntityForUpdate(id);
        assertVisible(count, approverId, visibleWarehouseIds, false, false);
        if (count.getStatus() != CycleCountStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ duyệt khi phiếu đang PENDING_REVIEW (hiện tại: " + count.getStatus() + ")");
        }

        List<CycleCountItem> countedItems = count.getItems().stream()
                .filter(item -> item.getStatus() == CycleCountItem.ItemStatus.COUNTED)
                .toList();
        for (CycleCountItem item : countedItems) {
            assertStockSnapshotStillCurrent(item);
        }

        CycleCountResponse before = toResponse(count);
        for (CycleCountItem item : countedItems) {
            if (item.getDiscrepancy() != null && item.getDiscrepancy() != 0) {
                stockLevelService.adjust(new StockAdjustCommand(
                        count.getWarehouseId(),
                        item.getLocationId(),
                        item.getProductId(),
                        normalizeLot(item.getLotNumber()),
                        item.getDiscrepancy(),
                        "CYCLE_COUNT:" + count.getId() + ":" + item.getId(),
                        "CYCLE_COUNT",
                        count.getId()
                ));
            }
            item.setStatus(CycleCountItem.ItemStatus.ADJUSTED);
            cycleCountItemRepository.save(item);
        }

        count.setStatus(CycleCountStatus.APPROVED);
        count.setCompletedAt(OffsetDateTime.now());
        count.setApprovedBy(approverId);
        CycleCount saved = cycleCountRepository.save(count);
        CycleCountResponse after = toResponse(saved);
        auditLogService.record("CYCLE_COUNT", "APPROVE", "Duyệt kết quả kiểm kê và điều chỉnh tồn kho",
                "CYCLE_COUNT", saved.getId(), displayCycleCount(saved), before, after, null, cycleCountMetadata(saved));
        notifyApproved(saved);
        return after;
    }

    @Transactional
    public CycleCountResponse rejectForRecount(UUID id, String reason, UUID rejectedBy, Collection<UUID> visibleWarehouseIds) {
        CycleCount count = getEntityForUpdate(id);
        assertVisible(count, rejectedBy, visibleWarehouseIds, false, false);
        if (count.getStatus() != CycleCountStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ yêu cầu kiểm lại khi phiếu đang PENDING_REVIEW (hiện tại: " + count.getStatus() + ")");
        }
        CycleCountResponse before = toResponse(count);
        count.setStatus(CycleCountStatus.RECOUNT_REQUIRED);
        count.setRejectedBy(rejectedBy);
        count.setRejectedAt(OffsetDateTime.now());
        count.setRejectionReason(reason == null ? null : reason.trim());
        count.getItems().forEach(item -> {
            item.setStatus(CycleCountItem.ItemStatus.PENDING);
            item.setCountedQty(null);
            item.setDiscrepancy(null);
        });

        CycleCount saved = cycleCountRepository.save(count);
        CycleCountResponse after = toResponse(saved);
        auditLogService.record("CYCLE_COUNT", "REJECT", "Yêu cầu kiểm kê lại",
                "CYCLE_COUNT", saved.getId(), displayCycleCount(saved), before, after, reason, cycleCountMetadata(saved));
        notifyRejected(saved);
        return after;
    }

    @Transactional
    public CycleCountResponse cancel(UUID id) {
        return cancel(id, null);
    }

    @Transactional
    public CycleCountResponse cancel(UUID id, Collection<UUID> visibleWarehouseIds) {
        CycleCount count = getEntityForUpdate(id);
        assertVisible(count, null, visibleWarehouseIds, false, false);
        if (count.getStatus() != CycleCountStatus.PENDING
                && count.getStatus() != CycleCountStatus.IN_PROGRESS
                && count.getStatus() != CycleCountStatus.RECOUNT_REQUIRED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ hủy khi phiếu đang PENDING, IN_PROGRESS hoặc RECOUNT_REQUIRED (hiện tại: " + count.getStatus() + ")");
        }

        CycleCountResponse before = toResponse(count);
        count.setStatus(CycleCountStatus.CANCELLED);
        CycleCount saved = cycleCountRepository.save(count);
        CycleCountResponse after = toResponse(saved);
        auditLogService.record("CYCLE_COUNT", "CANCEL", "Hủy phiếu kiểm kê",
                "CYCLE_COUNT", saved.getId(), displayCycleCount(saved), before, after, null, cycleCountMetadata(saved));
        return after;
    }

    private CycleCount getEntity(UUID id) {
        return cycleCountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu kiểm kê"));
    }

    private CycleCount getEntityForUpdate(UUID id) {
        return cycleCountRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu kiểm kê"));
    }

    private void assertStatus(CycleCount count, CycleCountStatus expected, String message) {
        if (count.getStatus() != expected) {
            throw new AppException(ErrorCode.BAD_REQUEST, message + " (hiện tại: " + count.getStatus() + ")");
        }
    }

    private void assertVisible(CycleCount count, UUID actorId, Collection<UUID> visibleWarehouseIds,
            boolean staffOnly, boolean reportOnly) {
        if (visibleWarehouseIds != null
                && (visibleWarehouseIds.isEmpty() || !visibleWarehouseIds.contains(count.getWarehouseId()))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được thao tác phiếu kiểm kê của kho này");
        }
        if (staffOnly && (actorId == null || count.getAssignedTo() == null || !actorId.equals(count.getAssignedTo()))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn chỉ được thao tác phiếu kiểm kê được giao cho bạn");
        }
        if (reportOnly && count.getStatus() != CycleCountStatus.APPROVED) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản báo cáo chỉ được xem kết quả kiểm kê đã duyệt");
        }
    }

    private Specification<CycleCount> buildSpec(String keyword, String status, UUID warehouseId,
            UUID actorId, Collection<UUID> visibleWarehouseIds, boolean staffOnly, boolean reportOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (visibleWarehouseIds != null) {
                predicates.add(visibleWarehouseIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("warehouseId").in(visibleWarehouseIds));
            }
            if (staffOnly) {
                predicates.add(actorId == null ? cb.disjunction() : cb.equal(root.get("assignedTo"), actorId));
            }
            if (reportOnly) {
                predicates.add(cb.equal(root.get("status"), CycleCountStatus.APPROVED));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("countNumber")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                try {
                    predicates.add(cb.equal(root.get("status"), CycleCountStatus.valueOf(status.trim().toUpperCase())));
                } catch (IllegalArgumentException ignored) {
                    predicates.add(cb.disjunction());
                }
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<StockLevelRepository.CycleCountStockSnapshotView> resolveManualStockSnapshots(CreateCycleCountRequest request) {
        return request.items().stream()
                .map(req -> stockLevelRepository.findCycleCountSnapshot(
                        request.warehouseId(), req.locationId(), req.productId(), normalizeLot(req.lotNumber()))
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                                "Không tìm thấy tồn kho: product=" + req.productId()
                                        + ", location=" + req.locationId())))
                .toList();
    }

    private List<StockLevelRepository.CycleCountStockSnapshotView> resolveStockSnapshotsByScope(UUID warehouseId, String scope, String scopeValue) {
        return switch (scope.trim().toUpperCase()) {
            case "WAREHOUSE" -> stockLevelRepository.findCycleCountSnapshotsByWarehouseId(warehouseId);
            case "ZONE" -> {
                if (!StringUtils.hasText(scopeValue)) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "scopeValue là bắt buộc cho phạm vi ZONE");
                }
                yield stockLevelRepository.findCycleCountSnapshotsByWarehouseIdAndZone(warehouseId, scopeValue.trim());
            }
            case "LOCATION" -> {
                if (!StringUtils.hasText(scopeValue)) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "scopeValue là bắt buộc cho phạm vi LOCATION");
                }
                UUID locationId = parseUuid(scopeValue, "scopeValue LOCATION phải là UUID vị trí");
                yield stockLevelRepository.findCycleCountSnapshotsByWarehouseIdAndLocationId(warehouseId, locationId);
            }
            case "PRODUCT" -> {
                if (!StringUtils.hasText(scopeValue)) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "scopeValue là bắt buộc cho phạm vi PRODUCT");
                }
                yield stockLevelRepository.findCycleCountSnapshotsByWarehouseIdAndProductId(
                        warehouseId, parseUuid(scopeValue, "scopeValue PRODUCT phải là UUID sản phẩm"));
            }
            default -> throw new AppException(ErrorCode.BAD_REQUEST,
                    "Phạm vi không hợp lệ: " + scope + ". Chọn WAREHOUSE, ZONE, LOCATION hoặc PRODUCT.");
        };
    }

    private CycleCountItem findItem(CycleCount count, RecordCountRequest.ItemResult result) {
        if (result.itemId() != null) {
            return count.getItems().stream()
                    .filter(i -> i.getId().equals(result.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy dòng kiểm kê: " + result.itemId()));
        }
        return count.getItems().stream()
                .filter(i -> i.getProductId().equals(result.productId())
                        && i.getLocationId().equals(result.locationId())
                        && normalizeLot(i.getLotNumber()).equals(normalizeLot(result.lotNumber())))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy dòng kiểm kê: product=" + result.productId()
                                + ", location=" + result.locationId()));
    }

    private void assertStockSnapshotStillCurrent(CycleCountItem item) {
        String lotNumber = normalizeLot(item.getLotNumber());
        StockLevel current = stockLevelRepository
                .findByLocationIdAndProductIdAndLotNumber(item.getLocationId(), item.getProductId(), lotNumber)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                        "Tồn kho đã thay đổi sau khi tạo kiểm kê; vui lòng tạo phiếu kiểm kê mới"));
        int currentQty = safeQty(current.getQtyOnHand());
        int snapshotQty = safeQty(item.getSystemQty());
        if (currentQty != snapshotQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tồn kho đã thay đổi sau khi tạo kiểm kê cho productId=" + item.getProductId()
                            + ", locationId=" + item.getLocationId()
                            + ", lot=" + lotNumber
                            + " (snapshot=" + snapshotQty + ", hiện tại=" + currentQty
                            + "). Vui lòng kiểm kê lại trước khi duyệt.");
        }
    }

    private CycleCountResponse toResponse(CycleCount count) {
        String warehouseName = count.getWarehouseId() == null ? null : warehouseRepository.findById(count.getWarehouseId())
                .map(Warehouse::getName).orElse(null);

        List<CycleCountResponse.LineResponse> lines = toLineResponses(count);
        int totalLines = lines.size();
        int countedLines = (int) lines.stream().filter(line -> line.countedQty() != null).count();
        int discrepancyLines = (int) lines.stream().filter(line -> line.discrepancy() != null && line.discrepancy() != 0).count();
        int totalAbsDiscrepancy = lines.stream()
                .filter(line -> line.discrepancy() != null)
                .mapToInt(line -> Math.abs(line.discrepancy()))
                .sum();

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
                count.getSubmittedAt(),
                count.getCompletedAt(),
                count.getAssignedTo(),
                count.getCreatedBy(),
                count.getApprovedBy(),
                count.getRejectedBy(),
                count.getRejectedAt(),
                count.getRejectionReason(),
                count.getCreatedAt(),
                totalLines,
                countedLines,
                discrepancyLines,
                totalAbsDiscrepancy,
                lines
        );
    }

    private CycleCountResponse toSummaryResponse(CycleCount count,
            CycleCountItemRepository.CycleCountItemStatsView stats,
            Map<UUID, String> warehouseNamesById) {
        int totalLines = stats == null ? 0 : safeLongToInt(stats.getTotalLines());
        int countedLines = stats == null ? 0 : safeLongToInt(stats.getCountedLines());
        int discrepancyLines = stats == null ? 0 : safeLongToInt(stats.getDiscrepancyLines());
        int totalAbsDiscrepancy = stats == null ? 0 : safeLongToInt(stats.getTotalAbsDiscrepancy());

        return new CycleCountResponse(
                count.getId(),
                count.getCountNumber(),
                count.getWarehouseId(),
                warehouseNamesById.get(count.getWarehouseId()),
                count.getStatus().name(),
                count.getDescription(),
                count.getScope(),
                count.getScopeValue(),
                count.getScheduledAt(),
                count.getStartedAt(),
                count.getSubmittedAt(),
                count.getCompletedAt(),
                count.getAssignedTo(),
                count.getCreatedBy(),
                count.getApprovedBy(),
                count.getRejectedBy(),
                count.getRejectedAt(),
                count.getRejectionReason(),
                count.getCreatedAt(),
                totalLines,
                countedLines,
                discrepancyLines,
                totalAbsDiscrepancy,
                List.of());
    }

    private CycleCountResponse toCreateResponse(CycleCount count, List<CycleCountItem> items) {
        String warehouseName = count.getWarehouseId() == null ? null : warehouseRepository.findById(count.getWarehouseId())
                .map(Warehouse::getName).orElse(null);
        int totalLines = items == null ? 0 : items.size();

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
                count.getSubmittedAt(),
                count.getCompletedAt(),
                count.getAssignedTo(),
                count.getCreatedBy(),
                count.getApprovedBy(),
                count.getRejectedBy(),
                count.getRejectedAt(),
                count.getRejectionReason(),
                count.getCreatedAt(),
                totalLines,
                0,
                0,
                0,
                List.of());
    }

    private int safeLongToInt(Long value) {
        if (value == null) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private List<CycleCountResponse.LineResponse> toLineResponses(CycleCount count) {
        if (count.getItems() == null || count.getItems().isEmpty()) {
            return List.of();
        }
        Set<UUID> productIds = count.getItems().stream()
                .map(CycleCountItem::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> locationIds = count.getItems().stream()
                .map(CycleCountItem::getLocationId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, ProductSummaryResponse> productMap = loadProducts(productIds);
        Map<UUID, Location> locationMap = locationRepository.findAllById(locationIds)
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));

        return count.getItems().stream().map(item -> {
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
                    varianceSeverity(item.getDiscrepancy(), item.getSystemQty()),
                    item.getStatus().name(),
                    item.getNotes()
            );
        }).toList();
    }

    private Map<UUID, ProductSummaryResponse> loadProducts(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return productService.findSummariesByIds(new ArrayList<>(ids))
                    .stream().collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));
        } catch (Exception e) {
            log.warn("Failed to load product summaries: {}", e.getMessage());
            return Map.of();
        }
    }

    private UserAccount requireActiveStaff(UUID userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên kiểm kê"));
        boolean isStaff = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> "WAREHOUSE_STAFF".equals(role.getCode()));
        if (!Boolean.TRUE.equals(user.getIsActive()) || !isStaff) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Người được giao kiểm kê phải là nhân viên kho đang hoạt động");
        }
        return user;
    }

    private void notifyCycleCountCreated(CycleCount count, UserAccount assignee) {
        if (assignee != null) {
            notificationService.create(new CreateNotificationCommand(
                    assignee.getId(),
                    NotificationType.CYCLE_COUNT_ASSIGNED,
                    NotificationSeverity.INFO,
                    "Bạn có phiếu kiểm kê mới",
                    "Phiếu kiểm kê " + displayCycleCount(count) + " đã được giao cho bạn",
                    "CYCLE_COUNT",
                    count.getId()));
            return;
        }
        notificationService.createForRoles(
                List.of("ADMIN"),
                NotificationType.CYCLE_COUNT_CREATED,
                NotificationSeverity.INFO,
                "Có phiếu kiểm kê mới",
                "Phiếu kiểm kê " + displayCycleCount(count) + " đã được tạo",
                "CYCLE_COUNT",
                count.getId());
        notifyWarehouseManagers(count, NotificationType.CYCLE_COUNT_CREATED, NotificationSeverity.INFO,
                "Có phiếu kiểm kê mới",
                "Phiếu kiểm kê " + displayCycleCount(count) + " đã được tạo");
    }

    private void notifySubmitted(CycleCount count) {
        notificationService.createForRoles(
                List.of("ADMIN"),
                NotificationType.CYCLE_COUNT_SUBMITTED,
                NotificationSeverity.WARNING,
                "Có kết quả kiểm kê chờ duyệt",
                "Phiếu kiểm kê " + displayCycleCount(count) + " đã được gửi duyệt",
                "CYCLE_COUNT",
                count.getId());
        notifyWarehouseManagers(count, NotificationType.CYCLE_COUNT_SUBMITTED, NotificationSeverity.WARNING,
                "Có kết quả kiểm kê chờ duyệt",
                "Phiếu kiểm kê " + displayCycleCount(count) + " đã được gửi duyệt");
    }

    private void notifyApproved(CycleCount count) {
        if (count.getAssignedTo() != null) {
            notificationService.create(new CreateNotificationCommand(
                    count.getAssignedTo(), NotificationType.CYCLE_COUNT_APPROVED, NotificationSeverity.INFO,
                    "Kết quả kiểm kê đã được duyệt",
                    "Phiếu kiểm kê " + displayCycleCount(count) + " đã được duyệt",
                    "CYCLE_COUNT", count.getId()));
        }
    }

    private void notifyRejected(CycleCount count) {
        if (count.getAssignedTo() != null) {
            notificationService.create(new CreateNotificationCommand(
                    count.getAssignedTo(), NotificationType.CYCLE_COUNT_REJECTED, NotificationSeverity.WARNING,
                    "Cần kiểm kê lại",
                    "Phiếu kiểm kê " + displayCycleCount(count) + " cần kiểm kê lại",
                    "CYCLE_COUNT", count.getId()));
        }
    }

    private void notifyStockDiscrepancies(CycleCount count) {
        long discrepancyCount = count.getItems() == null ? 0 : count.getItems().stream()
                .filter(item -> item.getDiscrepancy() != null && item.getDiscrepancy() != 0)
                .count();
        if (discrepancyCount == 0) {
            return;
        }
        notificationService.createForRoles(
                List.of("ADMIN"),
                NotificationType.STOCK_DISCREPANCY,
                NotificationSeverity.CRITICAL,
                "Phát hiện chênh lệch tồn kho",
                "Phiếu kiểm kê " + displayCycleCount(count) + " có " + discrepancyCount + " dòng chênh lệch",
                "CYCLE_COUNT",
                count.getId());
        notifyWarehouseManagers(count, NotificationType.STOCK_DISCREPANCY, NotificationSeverity.CRITICAL,
                "Phát hiện chênh lệch tồn kho",
                "Phiếu kiểm kê " + displayCycleCount(count) + " có " + discrepancyCount + " dòng chênh lệch");
    }

    private void notifyWarehouseManagers(CycleCount count, NotificationType type, NotificationSeverity severity,
            String title, String message) {
        warehouseRepository.findById(count.getWarehouseId())
                .map(Warehouse::getManagers)
                .orElse(Set.of())
                .stream()
                .filter(manager -> Boolean.TRUE.equals(manager.getIsActive()))
                .forEach(manager -> notificationService.create(new CreateNotificationCommand(
                        manager.getId(), type, severity, title, message, "CYCLE_COUNT", count.getId())));
    }

    private Map<String, Object> cycleCountMetadata(CycleCount count) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("countNumber", displayCycleCount(count));
        metadata.put("warehouseId", count.getWarehouseId());
        metadata.put("assignedTo", count.getAssignedTo());
        metadata.put("status", count.getStatus() == null ? null : count.getStatus().name());
        metadata.put("totalItems", count.getItems() == null ? 0 : count.getItems().size());
        metadata.put("discrepancyItems", count.getItems() == null ? 0 : count.getItems().stream()
                .filter(item -> item.getDiscrepancy() != null && item.getDiscrepancy() != 0).count());
        return metadata;
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private int safeQty(Integer qty) {
        return qty == null ? 0 : qty;
    }

    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private String displayCycleCount(CycleCount count) {
        return StringUtils.hasText(count.getCountNumber()) ? count.getCountNumber() : count.getId().toString();
    }

    private String varianceSeverity(Integer discrepancy, Integer systemQty) {
        if (discrepancy == null || discrepancy == 0) {
            return "NONE";
        }
        int abs = Math.abs(discrepancy);
        int base = Math.max(Math.abs(safeQty(systemQty)), 1);
        double ratio = abs / (double) base;
        if (abs >= 50 || ratio >= 0.2) {
            return "HIGH";
        }
        if (abs >= 10 || ratio >= 0.05) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
