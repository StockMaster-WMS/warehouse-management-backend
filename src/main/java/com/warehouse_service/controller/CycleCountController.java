package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.warehouse_service.dto.request.CreateCycleCountRequest;
import com.warehouse_service.dto.request.RecordCountRequest;
import com.warehouse_service.dto.response.CycleCountResponse;
import com.warehouse_service.service.CycleCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cycle-counts")
@Tag(name = "Cycle Count", description = "Quản lý kiểm kê kho")
@SecurityRequirement(name = "bearerAuth")
public class CycleCountController {

    private final CycleCountService cycleCountService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách các đợt kiểm kê")
    public ApiResponse<List<CycleCountResponse>> getAll() {
        return ApiResponse.success("Lấy danh sách kiểm kê thành công", cycleCountService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy chi tiết một đợt kiểm kê")
    public ApiResponse<CycleCountResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy thông tin kiểm kê thành công", cycleCountService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo đợt kiểm kê mới")
    public ApiResponse<CycleCountResponse> create(@Valid @RequestBody CreateCycleCountRequest request) {
        // In a real app, get current user ID from token
        return ApiResponse.success("Tạo đợt kiểm kê thành công", cycleCountService.create(request, null));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Bắt đầu thực hiện kiểm kê")
    public ApiResponse<CycleCountResponse> start(@PathVariable UUID id) {
        return ApiResponse.success("Bắt đầu kiểm kê thành công", cycleCountService.startCounting(id));
    }

    @PostMapping("/{id}/record")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Ghi nhận số lượng đếm được")
    public ApiResponse<CycleCountResponse> record(@PathVariable UUID id, @Valid @RequestBody RecordCountRequest request) {
        return ApiResponse.success("Ghi nhận số lượng thành công", cycleCountService.recordCount(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Hoàn tất và duyệt chênh lệch kiểm kê")
    public ApiResponse<CycleCountResponse> complete(@PathVariable UUID id) {
        return ApiResponse.success("Hoàn tất và điều chỉnh kho thành công", cycleCountService.completeAndAdjust(id, null));
    }
}
