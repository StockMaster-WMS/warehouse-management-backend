package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.inbound_service.dto.request.CancelRmaRequest;
import com.inbound_service.dto.request.CreateRmaRequest;
import com.inbound_service.dto.request.ReceiveRmaRequest;
import com.inbound_service.dto.request.RejectRmaRequest;
import com.inbound_service.dto.response.RmaReportResponse;
import com.inbound_service.dto.response.RmaResponse;
import com.inbound_service.service.RmaService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'REPORT_VIEWER')")
    @Operation(summary = "Lấy danh sách yêu cầu trả hàng")
    public ApiResponse<List<RmaResponse>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, warehouseId);
        return ApiResponse.success("Lấy danh sách RMA thành công",
                rmaService.getAll(keyword, status, reason, warehouseId, createdFrom, createdTo,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'REPORT_VIEWER')")
    @Operation(summary = "Lấy chi tiết yêu cầu trả hàng")
    public ApiResponse<RmaResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy thông tin RMA thành công",
                rmaService.getById(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo yêu cầu trả hàng mới")
    public ApiResponse<RmaResponse> create(@Valid @RequestBody CreateRmaRequest request, Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Tạo RMA thành công", rmaService.create(request, currentUserId(authentication)));
    }

    @PostMapping("/customer")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo phiếu khách trả")
    public ApiResponse<RmaResponse> createCustomerReturn(@Valid @RequestBody CreateRmaRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Tạo phiếu khách trả thành công",
                rmaService.createCustomerReturn(request, currentUserId(authentication)));
    }

    @PostMapping("/supplier")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo phiếu trả nhà cung cấp")
    public ApiResponse<RmaResponse> createSupplierReturn(@Valid @RequestBody CreateRmaRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Tạo phiếu trả nhà cung cấp thành công",
                rmaService.createSupplierReturn(request, currentUserId(authentication)));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Ghi nhận nhận hàng trả lại")
    public ApiResponse<RmaResponse> receive(@PathVariable UUID id,
            @Valid @RequestBody ReceiveRmaRequest request,
            Authentication authentication) {
        return ApiResponse.success("Ghi nhận nhận hàng thành công",
                rmaService.receiveItem(id, request, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Hoàn tất quy trình trả hàng")
    public ApiResponse<RmaResponse> complete(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Hoàn tất RMA thành công",
                rmaService.complete(id, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Duyệt phiếu trả hàng")
    public ApiResponse<RmaResponse> approve(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Duyệt phiếu trả hàng thành công",
                rmaService.approve(id, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Từ chối phiếu trả hàng")
    public ApiResponse<RmaResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectRmaRequest request,
            Authentication authentication) {
        return ApiResponse.success("Từ chối phiếu trả hàng thành công",
                rmaService.reject(id, request.reason(), currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Hủy yêu cầu trả hàng")
    public ApiResponse<RmaResponse> cancel(@PathVariable UUID id,
            @RequestBody(required = false) CancelRmaRequest request,
            Authentication authentication) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success("Hủy RMA thành công",
                rmaService.cancel(id, reason, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'REPORT_VIEWER')")
    @Operation(summary = "Báo cáo trả hàng")
    public ApiResponse<RmaReportResponse> report(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String returnType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, warehouseId);
        return ApiResponse.success("Lấy báo cáo trả hàng thành công",
                rmaService.getReport(warehouseId, returnType, createdFrom, createdTo,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }
}
