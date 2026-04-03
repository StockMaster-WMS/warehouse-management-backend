package com.inbound_service.service;

import com.common.api.PagedResponse;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayTaskService {

    private final PutawayTaskRepository putawayTaskRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final PutawayTaskMapper putawayTaskMapper;

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

    public PutawayTaskResponse findById(UUID id) {
        return putawayTaskMapper.toResponse(getTask(id));
    }

    private static final EnumSet<PutawayStatus> TERMINAL_STATUSES =
            EnumSet.of(PutawayStatus.COMPLETED, PutawayStatus.CANCELLED);

    private static final EnumSet<PutawayStatus> UPDATABLE_STATUSES =
            EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS, PutawayStatus.CANCELLED);

    @Transactional
    public PutawayTaskResponse update(UUID id, UpdatePutawayTaskRequest request) {
        PutawayTask task = getTask(id);
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
        return putawayTaskMapper.toResponse(putawayTaskRepository.save(task));
    }

    private PutawayStatus parsePutawayStatus(String raw) {
        try {
            return PutawayStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái putaway không hợp lệ: " + raw);
        }
    }

    /**
     * Hoàn tất putaway: ghi nhận vị trí thực tế trong kho.
     * Tồn kho đã được cập nhật khi tạo phiếu nhập kho (InboundReceiptService).
     */
    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request) {
        PutawayTask task = putawayTaskRepository.findByIdWithPoAndOrderForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));

        if (!EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS).contains(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ hoàn tất putaway ở trạng thái PENDING hoặc IN_PROGRESS");
        }

        task.setActualLocationId(request.actualLocationId());
        task.setStatus(PutawayStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        PutawayTaskResponse response = putawayTaskMapper.toResponse(putawayTaskRepository.save(task));

        // Cập nhật trạng thái phiếu nhập kho nếu tất cả putaway hoàn tất
        if (task.getInboundReceipt() != null) {
            refreshReceiptStatus(task.getInboundReceipt().getId());
        }

        return response;
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

    private PutawayTask getTask(UUID id) {
        return putawayTaskRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));
    }
}
