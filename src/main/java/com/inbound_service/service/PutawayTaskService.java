package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.request.UpdatePutawayTaskRequest;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptStatus;
import com.inbound_service.entity.PutawayStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import com.inbound_service.repository.PutawayTaskSpecification;
import com.warehouse_service.entity.Location;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.service.StockLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        assertLocationInReceiptWarehouse(task, request.actualLocationId());

        PutawayTaskResponse before = putawayTaskMapper.toResponse(task);
        task.setActualLocationId(request.actualLocationId());
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
        StockAdjustCommand deductCmd = new StockAdjustCommand(
                task.getInboundReceipt().getWarehouseId(),
                task.getInboundReceipt().getLocationId(),
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
                task.getActualLocationId(),
                task.getProductId(),
                null,
                task.getQtyToPutaway(),
                "PUTAWAY_MOVE_IN_" + task.getId(),
                "PUTAWAY_TASK",
                task.getId()
        );
        stockLevelService.adjust(addCmd);
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
