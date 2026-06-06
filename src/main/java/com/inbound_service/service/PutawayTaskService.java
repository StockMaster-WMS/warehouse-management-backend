package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.request.UpdatePutawayTaskRequest;
import com.inbound_service.dto.response.PutawayLocationSuggestionResponse;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptItem;
import com.inbound_service.entity.InboundReceiptStatus;
import com.inbound_service.entity.PutawayStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.InboundReceiptItemRepository;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import com.inbound_service.repository.PutawayTaskSpecification;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.service.StockLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayTaskService {

    private static final EnumSet<PutawayStatus> TERMINAL_STATUSES =
            EnumSet.of(PutawayStatus.COMPLETED, PutawayStatus.CANCELLED);

    private static final EnumSet<PutawayStatus> UPDATABLE_STATUSES =
            EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS, PutawayStatus.CANCELLED);

    private final PutawayTaskRepository putawayTaskRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final PutawayTaskMapper putawayTaskMapper;
    private final AuditLogService auditLogService;
    private final StockLevelService stockLevelService;
    private final LocationRepository locationRepository;
    private final StockLevelRepository stockLevelRepository;
    private final InboundReceiptItemRepository inboundReceiptItemRepository;

    public PagedResponse<PutawayTaskResponse> findAll(Pageable pageable, UUID poItemId, String status) {
        return findAll(pageable, poItemId, status, (Collection<UUID>) null);
    }

    public PagedResponse<PutawayTaskResponse> findAll(Pageable pageable, UUID poItemId, String status, UUID assignedTo) {
        Specification<PutawayTask> spec = PutawayTaskSpecification.hasPoItemId(poItemId)
                .and(PutawayTaskSpecification.hasAssignedTo(assignedTo))
                .and(PutawayTaskSpecification.hasStatus(status));
        return mapPage(putawayTaskRepository.findAll(spec, pageable));
    }

    public PagedResponse<PutawayTaskResponse> findAll(Pageable pageable, UUID poItemId, String status,
            Collection<UUID> visibleWarehouseIds) {
        Specification<PutawayTask> spec = PutawayTaskSpecification.hasWarehouseIds(visibleWarehouseIds)
                .and(PutawayTaskSpecification.hasPoItemId(poItemId))
                .and(PutawayTaskSpecification.hasStatus(status));
        return mapPage(putawayTaskRepository.findAll(spec, pageable));
    }

    public PutawayTaskResponse findById(UUID id) {
        return findById(id, (Collection<UUID>) null);
    }

    public PutawayTaskResponse findById(UUID id, UUID actorId, boolean canBypassAssignment) {
        return findById(id, (Collection<UUID>) null);
    }

    public PutawayTaskResponse findById(UUID id, Collection<UUID> visibleWarehouseIds) {
        PutawayTask task = getTask(id);
        assertPutawayWarehouseVisible(task, visibleWarehouseIds);
        return putawayTaskMapper.toResponse(task);
    }

    public List<PutawayLocationSuggestionResponse> suggestLocations(UUID id, int limit,
            Collection<UUID> visibleWarehouseIds) {
        PutawayTask task = getTask(id);
        assertPutawayWarehouseVisible(task, visibleWarehouseIds);

        UUID warehouseId = receiptWarehouseId(task);
        if (warehouseId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Nhiem vu xep hang chua gan voi phieu nhap kho hop le");
        }

        int max = limit <= 0 ? 30 : Math.min(limit, 100);
        List<Location> allLocations = locationRepository.findByWarehouseId(warehouseId).stream()
                .filter(this::isPutawayStorageLocation)
                .sorted(Comparator.comparing(Location::getCode, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<UUID, Location> locationById = new LinkedHashMap<>();
        for (Location location : allLocations) {
            locationById.put(location.getId(), location);
        }

        Set<UUID> usedLocationIds = new HashSet<>();
        for (StockLevel stock : stockLevelRepository.findByWarehouseId(warehouseId)) {
            if (stock.getLocation() != null && safeQty(stock.getQtyOnHand()) > 0) {
                usedLocationIds.add(stock.getLocation().getId());
            }
        }

        Map<UUID, Integer> qtyByProductLocation = new LinkedHashMap<>();
        for (StockLevel stock : stockLevelRepository
                .findByWarehouseIdAndProductIdWithDetails(warehouseId, task.getProductId())) {
            if (stock.getLocation() != null
                    && safeQty(stock.getQtyOnHand()) > 0
                    && locationById.containsKey(stock.getLocation().getId())) {
                qtyByProductLocation.merge(stock.getLocation().getId(), stock.getQtyOnHand(), Integer::sum);
            }
        }

        LinkedHashMap<UUID, PutawayLocationSuggestionResponse> suggestions = new LinkedHashMap<>();
        addSuggestion(suggestions, locationById.get(task.getSuggestedLocationId()), task, usedLocationIds,
                qtyByProductLocation);

        qtyByProductLocation.keySet().stream()
                .map(locationById::get)
                .filter(location -> location != null)
                .sorted(Comparator.comparing(Location::getCode, String.CASE_INSENSITIVE_ORDER))
                .forEach(location -> addSuggestion(suggestions, location, task, usedLocationIds, qtyByProductLocation));

        allLocations.stream()
                .filter(location -> !usedLocationIds.contains(location.getId()))
                .forEach(location -> addSuggestion(suggestions, location, task, usedLocationIds, qtyByProductLocation));

        if (suggestions.size() < max) {
            allLocations.forEach(location -> addSuggestion(suggestions, location, task, usedLocationIds,
                    qtyByProductLocation));
        }

        return new ArrayList<>(suggestions.values()).stream()
                .limit(max)
                .toList();
    }

    @Transactional
    public PutawayTaskResponse update(UUID id, UpdatePutawayTaskRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public PutawayTaskResponse update(UUID id, UpdatePutawayTaskRequest request, Collection<UUID> visibleWarehouseIds) {
        PutawayTask task = getTask(id);
        assertPutawayWarehouseVisible(task, visibleWarehouseIds);
        PutawayTaskResponse before = putawayTaskMapper.toResponse(task);

        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không cập nhật nhiệm vụ xếp hàng đã kết thúc");
        }

        if (request.suggestedLocationId() != null) {
            assertLocationInReceiptWarehouse(task, request.suggestedLocationId());
            assertPutawayLocationEligible(request.suggestedLocationId());
            task.setSuggestedLocationId(request.suggestedLocationId());
        }

        if (request.status() != null && !request.status().isBlank()) {
            PutawayStatus newStatus = parsePutawayStatus(request.status());
            if (!UPDATABLE_STATUSES.contains(newStatus)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái putaway không hợp lệ");
            }
            task.setStatus(newStatus);
        }

        PutawayTask saved = putawayTaskRepository.save(task);
        PutawayTaskResponse after = putawayTaskMapper.toResponse(saved);
        auditLogService.record("PUTAWAY", "UPDATE", "Cập nhật nhiệm vụ xếp hàng",
                "PUTAWAY_TASK", saved.getId(), putawayEntityName(saved), before, after,
                null, putawayMetadata(saved));
        return after;
    }

    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request) {
        return complete(id, request, (Collection<UUID>) null);
    }

    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request, UUID actorId, boolean canBypassAssignment) {
        return complete(id, request, (Collection<UUID>) null);
    }

    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request, Collection<UUID> visibleWarehouseIds) {
        PutawayTask task = putawayTaskRepository.findByIdWithPoAndOrderForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhiệm vụ xếp hàng"));

        assertPutawayWarehouseVisible(task, visibleWarehouseIds);
        if (!EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS).contains(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ hoàn tất nhiệm vụ xếp hàng ở trạng thái PENDING hoặc IN_PROGRESS");
        }
        UUID actualLocationId = request != null && request.actualLocationId() != null
                ? request.actualLocationId()
                : task.getSuggestedLocationId();
        if (actualLocationId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Can chon vi tri thuc te de hoan tat nhiem vu xep hang");
        }
        assertLocationInReceiptWarehouse(task, actualLocationId);
        assertPutawayLocationEligible(actualLocationId);

        PutawayTaskResponse before = putawayTaskMapper.toResponse(task);
        task.setActualLocationId(actualLocationId);
        task.setStatus(PutawayStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        PutawayTask saved = putawayTaskRepository.save(task);
        PutawayTaskResponse response = putawayTaskMapper.toResponse(saved);

        if (saved.getInboundReceipt() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể hoàn tất nhiệm vụ xếp hàng không gắn với phiếu nhập kho");
        }

        moveStockFromReceivingLocation(saved);
        refreshReceiptStatus(saved.getInboundReceipt().getId());

        auditLogService.record("PUTAWAY", "PUTAWAY", "Hoàn tất nhiệm vụ xếp hàng",
                "PUTAWAY_TASK", saved.getId(), putawayEntityName(saved), before, response,
                null, putawayMetadata(saved));

        return response;
    }

    private PagedResponse<PutawayTaskResponse> mapPage(Page<PutawayTask> page) {
        Page<PutawayTaskResponse> mapped = page.map(putawayTaskMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    private PutawayStatus parsePutawayStatus(String raw) {
        try {
            return PutawayStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái putaway không hợp lệ: " + raw);
        }
    }

    private void moveStockFromReceivingLocation(PutawayTask task) {
        UUID sourceLocationId = resolveReceivingLocationId(task);
        UUID targetLocationId = task.getActualLocationId();
        if (sourceLocationId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Nhiem vu xep hang chua co vi tri nguon de chuyen ton");
        }
        if (sourceLocationId != null && sourceLocationId.equals(targetLocationId)) {
            return;
        }

        StockAdjustCommand deductCmd = new StockAdjustCommand(
                task.getInboundReceipt().getWarehouseId(),
                sourceLocationId,
                task.getProductId(),
                null,
                -task.getQtyToPutaway(),
                "PUTAWAY_MOVE_OUT_" + task.getId(),
                "PUTAWAY_TASK",
                task.getId()
        );
        stockLevelService.adjust(deductCmd);

        StockAdjustCommand addCmd = new StockAdjustCommand(
                task.getInboundReceipt().getWarehouseId(),
                targetLocationId,
                task.getProductId(),
                null,
                task.getQtyToPutaway(),
                "PUTAWAY_MOVE_IN_" + task.getId(),
                "PUTAWAY_TASK",
                task.getId()
        );
        stockLevelService.adjust(addCmd);
    }

    private UUID resolveReceivingLocationId(PutawayTask task) {
        InboundReceipt receipt = task.getInboundReceipt();
        if (receipt == null) {
            return null;
        }

        List<InboundReceiptItem> receiptItems = inboundReceiptItemRepository.findByReceiptId(receipt.getId());
        UUID poItemId = task.getPoItem() == null ? null : task.getPoItem().getId();
        UUID suggestedLocationId = task.getSuggestedLocationId();

        return receiptItems.stream()
                .filter(item -> task.getProductId().equals(item.getProductId()))
                .filter(item -> poItemId == null || item.getPoItem() == null || poItemId.equals(item.getPoItem().getId()))
                .filter(item -> suggestedLocationId == null || suggestedLocationId.equals(item.getLocationId()))
                .findFirst()
                .or(() -> receiptItems.stream()
                        .filter(item -> task.getProductId().equals(item.getProductId()))
                        .filter(item -> poItemId == null || item.getPoItem() == null || poItemId.equals(item.getPoItem().getId()))
                        .filter(item -> task.getQtyToPutaway().equals(item.getReceivedQty()))
                        .findFirst())
                .or(() -> receiptItems.stream()
                        .filter(item -> task.getProductId().equals(item.getProductId()))
                        .filter(item -> poItemId == null || item.getPoItem() == null || poItemId.equals(item.getPoItem().getId()))
                        .findFirst())
                .map(InboundReceiptItem::getLocationId)
                .orElse(receipt.getLocationId());
    }

    private void refreshReceiptStatus(UUID receiptId) {
        List<PutawayTask> tasks = putawayTaskRepository.findByInboundReceiptId(receiptId);
        if (tasks.isEmpty()) {
            return;
        }
        boolean allCompleted = tasks.stream()
                .allMatch(t -> t.getStatus() == PutawayStatus.COMPLETED);
        boolean anyInProgress = tasks.stream()
                .anyMatch(t -> t.getStatus() == PutawayStatus.IN_PROGRESS
                        || t.getStatus() == PutawayStatus.COMPLETED);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId).orElse(null);
        if (receipt == null) {
            return;
        }

        if (allCompleted) {
            receipt.setStatus(InboundReceiptStatus.COMPLETED);
        } else if (anyInProgress) {
            receipt.setStatus(InboundReceiptStatus.PUTAWAY_IN_PROGRESS);
        }
        inboundReceiptRepository.save(receipt);
    }

    private PutawayTask getTask(UUID id) {
        return putawayTaskRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhiệm vụ xếp hàng"));
    }

    private void assertPutawayWarehouseVisible(PutawayTask task, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds == null) {
            return;
        }
        UUID warehouseId = receiptWarehouseId(task);
        if (warehouseId == null || !visibleWarehouseIds.contains(warehouseId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được thao tác nhiệm vụ xếp hàng của kho này");
        }
    }

    private void assertLocationInReceiptWarehouse(PutawayTask task, UUID locationId) {
        UUID warehouseId = receiptWarehouseId(task);
        if (warehouseId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Nhiệm vụ xếp hàng chưa gắn với phiếu nhập kho hợp lệ");
        }
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy vị trí lưu kho"));
        if (location.getWarehouse() == null || !warehouseId.equals(location.getWarehouse().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Vị trí lưu kho không thuộc kho của phiếu nhập");
        }
    }

    private void addSuggestion(LinkedHashMap<UUID, PutawayLocationSuggestionResponse> suggestions,
            Location location, PutawayTask task, Set<UUID> usedLocationIds, Map<UUID, Integer> qtyByProductLocation) {
        if (location == null || suggestions.containsKey(location.getId())) {
            return;
        }
        UUID locationId = location.getId();
        boolean existingProductLocation = qtyByProductLocation.containsKey(locationId);
        suggestions.put(locationId, new PutawayLocationSuggestionResponse(
                locationId,
                location.getCode(),
                location.getLocationType(),
                location.getZone(),
                locationId.equals(task.getSuggestedLocationId()),
                existingProductLocation,
                !usedLocationIds.contains(locationId),
                existingProductLocation ? qtyByProductLocation.get(locationId) : 0));
    }

    private boolean isPutawayStorageLocation(Location location) {
        if (location == null || !Boolean.TRUE.equals(location.getIsActive())) {
            return false;
        }
        String status = StringUtils.hasText(location.getStatus())
                ? location.getStatus().trim().toUpperCase(Locale.ROOT)
                : "AVAILABLE";
        if (Set.of("BLOCKED", "MAINTENANCE", "INACTIVE", "DISABLED").contains(status)) {
            return false;
        }
        String type = StringUtils.hasText(location.getLocationType())
                ? location.getLocationType().trim().toUpperCase(Locale.ROOT)
                : "";
        return !type.startsWith("RMA_") && !"STAGING".equals(type);
    }

    private int safeQty(Integer qty) {
        return qty == null ? 0 : qty;
    }

    private void assertPutawayLocationEligible(UUID locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Khong tim thay vi tri luu kho"));
        if (!isPutawayStorageLocation(location)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Vi tri xep hang phai dang hoat dong, khong bi khoa va khong phai vi tri RMA/STAGING");
        }
    }

    private UUID receiptWarehouseId(PutawayTask task) {
        return task.getInboundReceipt() == null ? null : task.getInboundReceipt().getWarehouseId();
    }

    private String putawayEntityName(PutawayTask task) {
        return "product=" + task.getProductId() + ", qty=" + task.getQtyToPutaway();
    }

    private Map<String, Object> putawayMetadata(PutawayTask task) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("poItemId", task.getPoItem() == null ? null : task.getPoItem().getId());
        metadata.put("inboundReceiptId", task.getInboundReceipt() == null ? null : task.getInboundReceipt().getId());
        metadata.put("warehouseId", receiptWarehouseId(task));
        metadata.put("productId", task.getProductId());
        metadata.put("qtyToPutaway", task.getQtyToPutaway());
        metadata.put("suggestedLocationId", task.getSuggestedLocationId());
        metadata.put("actualLocationId", task.getActualLocationId());
        metadata.put("assignedTo", task.getAssignedTo());
        metadata.put("status", task.getStatus() == null ? null : task.getStatus().name());
        return metadata;
    }
}
