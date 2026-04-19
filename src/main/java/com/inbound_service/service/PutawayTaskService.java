package com.inbound_service.service;

import com.common.api.PagedResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayTaskService {

    private final PutawayTaskRepository putawayTaskRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final PutawayTaskMapper putawayTaskMapper;
    private final AuditLogService auditLogService;

    // Lấy danh sách putaway task có phân trang và lọc trạng thái.
    public PagedResponse<PutawayTaskResponse> findAll(Pageable pageable, UUID poItemId, String status) {
        Specification<PutawayTask> spec = PutawayTaskSpecification.hasPoItemId(poItemId)
                .and(PutawayTaskSpecification.hasStatus(status));
        Page<PutawayTask> page = putawayTaskRepository.findAll(spec, pageable);
        Page<PutawayTaskResponse> mapped = page.map(putawayTaskMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết putaway task theo id.
    public PutawayTaskResponse findById(UUID id) {
        return putawayTaskMapper.toResponse(getTask(id));
    }

    private static final EnumSet<PutawayStatus> TERMINAL_STATUSES =
            EnumSet.of(PutawayStatus.COMPLETED, PutawayStatus.CANCELLED);

    private static final EnumSet<PutawayStatus> UPDATABLE_STATUSES =
            EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS, PutawayStatus.CANCELLED);

        // Cập nhật thông tin putaway task khi chưa ở trạng thái kết thúc.
    @Transactional
    public PutawayTaskResponse update(UUID id, UpdatePutawayTaskRequest request) {
        PutawayTask task = getTask(id);
        PutawayTaskResponse before = putawayTaskMapper.toResponse(task);
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không cập nhật putaway đã kết thúc");
        }
        if (request.suggestedLocationId() != null) {
            task.setSuggestedLocationId(request.suggestedLocationId());
        }
        if (request.assignedTo() != null) {
            task.setAssignedTo(request.assignedTo());
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
        auditLogService.record("PUTAWAY", "UPDATE", "Cập nhật putaway",
                "PUTAWAY_TASK", saved.getId(), putawayEntityName(saved), before, after,
                null, putawayMetadata(saved));
        return after;
    }

    // Parse chuỗi trạng thái putaway về enum tương ứng.
    private PutawayStatus parsePutawayStatus(String raw) {
        try {
            return PutawayStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái putaway không hợp lệ: " + raw);
        }
    }

    // Hoàn tất putaway và cập nhật trạng thái phiếu nhập liên quan.
    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request) {
        PutawayTask task = putawayTaskRepository.findByIdWithPoAndOrderForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));

        if (!EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS).contains(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ hoàn tất putaway ở trạng thái PENDING hoặc IN_PROGRESS");
        }

        PutawayTaskResponse before = putawayTaskMapper.toResponse(task);
        task.setActualLocationId(request.actualLocationId());
        task.setStatus(PutawayStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        PutawayTask saved = putawayTaskRepository.save(task);
        PutawayTaskResponse response = putawayTaskMapper.toResponse(saved);

        // Cập nhật trạng thái phiếu nhập kho nếu tất cả putaway hoàn tất
        if (saved.getInboundReceipt() != null) {
            refreshReceiptStatus(saved.getInboundReceipt().getId());
        }

        auditLogService.record("PUTAWAY", "PUTAWAY", "Hoàn tất putaway",
                "PUTAWAY_TASK", saved.getId(), putawayEntityName(saved), before, response,
                null, putawayMetadata(saved));

        return response;
    }

    // Đồng bộ trạng thái inbound receipt theo tiến độ putaway.
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

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElse(null);
        if (receipt == null) return;

        if (allCompleted) {
            receipt.setStatus(InboundReceiptStatus.COMPLETED);
        } else if (anyInProgress) {
            receipt.setStatus(InboundReceiptStatus.PUTAWAY_IN_PROGRESS);
        }
        inboundReceiptRepository.save(receipt);
    }

    // Tìm thực thể putaway task theo id.
    private PutawayTask getTask(UUID id) {
        return putawayTaskRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));
    }

    private String putawayEntityName(PutawayTask task) {
        return "product=" + task.getProductId() + ", qty=" + task.getQtyToPutaway();
    }

    private Map<String, Object> putawayMetadata(PutawayTask task) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("poItemId", task.getPoItem() == null ? null : task.getPoItem().getId());
        metadata.put("inboundReceiptId", task.getInboundReceipt() == null ? null : task.getInboundReceipt().getId());
        metadata.put("productId", task.getProductId());
        metadata.put("qtyToPutaway", task.getQtyToPutaway());
        metadata.put("suggestedLocationId", task.getSuggestedLocationId());
        metadata.put("actualLocationId", task.getActualLocationId());
        metadata.put("assignedTo", task.getAssignedTo());
        metadata.put("status", task.getStatus() == null ? null : task.getStatus().name());
        return metadata;
    }
}
