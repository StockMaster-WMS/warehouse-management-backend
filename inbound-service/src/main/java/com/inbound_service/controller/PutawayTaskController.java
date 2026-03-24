package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.request.UpdatePutawayTaskRequest;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.service.PutawayTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/putaway-tasks")
@Tag(name = "Putaway APIs", description = "Nhiệm vụ đưa hàng vào vị trí sau khi nhận PO")
public class PutawayTaskController {

    private final PutawayTaskService putawayTaskService;

    @GetMapping
    @Operation(summary = "Danh sách putaway", description = "Lọc theo poItemId và/hoặc status")
    public ApiResponse<List<PutawayTaskResponse>> getAll(
            @Parameter(description = "ID dòng PO")
            @RequestParam(required = false) UUID poItemId,
            @Parameter(description = "PENDING | IN_PROGRESS | COMPLETED | CANCELLED")
            @RequestParam(required = false) String status) {
        return ApiResponse.success("Lấy danh sách putaway thành công",
                putawayTaskService.findAll(poItemId, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết putaway")
    public ApiResponse<PutawayTaskResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy putaway thành công", putawayTaskService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cập nhật putaway", description = "Gợi ý vị trí, người nhận, trạng thái (PENDING/IN_PROGRESS/CANCELLED)")
    public ApiResponse<PutawayTaskResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePutawayTaskRequest request) {
        return ApiResponse.success("Cập nhật putaway thành công", putawayTaskService.update(id, request));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Hoàn tất putaway", description = "Ghi nhận vị trí thực tế và cộng tồn kho (warehouse-service)")
    public ApiResponse<PutawayTaskResponse> complete(@PathVariable UUID id,
            @Valid @RequestBody CompletePutawayRequest request) {
        return ApiResponse.success("Hoàn tất putaway thành công", putawayTaskService.complete(id, request));
    }
}
