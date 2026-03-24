package com.inbound_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.client.WarehouseStockGateway;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.request.UpdatePutawayTaskRequest;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.PutawayTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayTaskService {

    private final PutawayTaskRepository putawayTaskRepository;
    private final PutawayTaskMapper putawayTaskMapper;
    private final WarehouseStockGateway warehouseStockGateway;

    public List<PutawayTaskResponse> findAll(UUID poItemId, String status) {
        List<PutawayTask> tasks;
        if (poItemId != null && status != null) {
            tasks = putawayTaskRepository.findByPoItemId(poItemId).stream()
                    .filter(t -> status.equalsIgnoreCase(t.getStatus()))
                    .toList();
        } else if (poItemId != null) {
            tasks = putawayTaskRepository.findByPoItemId(poItemId);
        } else if (status != null) {
            tasks = putawayTaskRepository.findByStatus(status);
        } else {
            tasks = putawayTaskRepository.findAll();
        }
        return tasks.stream().map(putawayTaskMapper::toResponse).toList();
    }

    public PutawayTaskResponse findById(UUID id) {
        return putawayTaskMapper.toResponse(getTask(id));
    }

    @Transactional
    public PutawayTaskResponse update(UUID id, UpdatePutawayTaskRequest request) {
        PutawayTask task = getTask(id);
        if ("COMPLETED".equalsIgnoreCase(task.getStatus()) || "CANCELLED".equalsIgnoreCase(task.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không cập nhật putaway đã kết thúc");
        }
        if (request.suggestedLocationId() != null) {
            task.setSuggestedLocationId(request.suggestedLocationId());
        }
        if (request.assignedTo() != null) {
            task.setAssignedTo(request.assignedTo());
        }
        if (request.status() != null && !request.status().isBlank()) {
            String s = request.status().trim().toUpperCase();
            if (!List.of("PENDING", "IN_PROGRESS", "CANCELLED").contains(s)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái putaway không hợp lệ");
            }
            task.setStatus(s);
        }
        return putawayTaskMapper.toResponse(putawayTaskRepository.save(task));
    }

    /**
     * Hoàn tất putaway: cộng tồn tại vị trí thực tế (warehouse-service). Gọi warehouse trước khi commit trạng thái COMPLETED.
     */
    @Transactional
    public PutawayTaskResponse complete(UUID id, CompletePutawayRequest request) {
        PutawayTask task = putawayTaskRepository.findByIdWithPoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));

        PoItem line = task.getPoItem();
        if (line == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Putaway không gắn dòng PO, không thể cập nhật tồn");
        }

        if (!List.of("PENDING", "IN_PROGRESS").contains(task.getStatus())) {
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
        task.setStatus("COMPLETED");
        task.setCompletedAt(OffsetDateTime.now());
        return putawayTaskMapper.toResponse(putawayTaskRepository.save(task));
    }

    private PutawayTask getTask(UUID id) {
        return putawayTaskRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy putaway"));
    }
}
