package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.client.warehouse.WarehouseStockGateway;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.request.UpdatePutawayTaskRequest;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PutawayStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PutawayTaskMapper;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayTaskService {

    private final PutawayTaskRepository putawayTaskRepository;
    private final PutawayTaskMapper putawayTaskMapper;
    private final WarehouseStockGateway warehouseStockGateway;

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
     * Hoàn tất putaway: cộng tồn tại vị trí thực tế (warehouse-service). Gọi warehouse trước khi commit trạng thái COMPLETED.
     */
    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request) {
        PutawayTask task = putawayTaskRepository.findByIdWithPoAndOrderForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));

        PoItem line = task.getPoItem();
        if (line == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Putaway không gắn dòng PO, không thể cập nhật tồn");
        }

        if (!EnumSet.of(PutawayStatus.PENDING, PutawayStatus.IN_PROGRESS).contains(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ hoàn tất putaway ở trạng thái PENDING hoặc IN_PROGRESS");
        }

        var po = line.getPurchaseOrder();
        StockAdjustCommand cmd = new StockAdjustCommand(
                po.getWarehouseId(),
                request.actualLocationId(),
                line.getProductId(),
                null,
                task.getQtyToPutaway());

        warehouseStockGateway.adjustOrThrow(cmd);

        task.setActualLocationId(request.actualLocationId());
        task.setStatus(PutawayStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        return putawayTaskMapper.toResponse(putawayTaskRepository.save(task));
    }

    private PutawayTask getTask(UUID id) {
        return putawayTaskRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));
    }
}
