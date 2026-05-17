package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.inbound_service.dto.request.CreateRmaRequest;
import com.inbound_service.dto.request.ReceiveRmaRequest;
import com.inbound_service.dto.response.RmaResponse;
import com.inbound_service.service.RmaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rma")
@Tag(name = "RMA / Returns", description = "Quản lý trả hàng từ khách hàng")
@SecurityRequirement(name = "bearerAuth")
public class RmaController {

    private final RmaService rmaService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách yêu cầu trả hàng")
    public ApiResponse<List<RmaResponse>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo) {
        return ApiResponse.success("Lấy danh sách RMA thành công",
                rmaService.getAll(keyword, status, reason, warehouseId, createdFrom, createdTo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy chi tiết yêu cầu trả hàng")
    public ApiResponse<RmaResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy thông tin RMA thành công", rmaService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo yêu cầu trả hàng mới")
    public ApiResponse<RmaResponse> create(@Valid @RequestBody CreateRmaRequest request) {
        return ApiResponse.success("Tạo RMA thành công", rmaService.create(request));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Ghi nhận nhận hàng trả lại")
    public ApiResponse<RmaResponse> receive(@PathVariable UUID id, @Valid @RequestBody ReceiveRmaRequest request) {
        return ApiResponse.success("Ghi nhận nhận hàng thành công", rmaService.receiveItem(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Hoàn tất quy trình trả hàng")
    public ApiResponse<RmaResponse> complete(@PathVariable UUID id) {
        return ApiResponse.success("Hoàn tất RMA thành công", rmaService.complete(id));
    }
}
