package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.dto.request.CreateCycleCountRequest;
import com.warehouse_service.dto.request.RecordCountRequest;
import com.warehouse_service.dto.request.RejectCycleCountRequest;
import com.warehouse_service.dto.response.CycleCountResponse;
import com.warehouse_service.service.CycleCountService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cycle-counts")
@Tag(name = "Cycle Count", description = "Quản lý kiểm kê kho")
@SecurityRequirement(name = "bearerAuth")
public class CycleCountController {

    private final CycleCountService cycleCountService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'REPORT_VIEWER')")
    @Operation(summary = "Danh sách phiếu kiểm kê")
    public ApiResponse<PagedResponse<CycleCountResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            Authentication authentication) {

        warehouseAccessService.assertCanAccessWarehouse(authentication, warehouseId);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));
        return ApiResponse.success("Lấy danh sách kiểm kê thành công",
                cycleCountService.getAll(pageable, keyword, status, warehouseId,
                        currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication),
                        staffOnly(authentication),
                        reportOnly(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'REPORT_VIEWER')")
    @Operation(summary = "Chi tiết phiếu kiểm kê")
    public ApiResponse<CycleCountResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy thông tin kiểm kê thành công",
                cycleCountService.getById(id,
                        currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication),
                        staffOnly(authentication),
                        reportOnly(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo phiếu kiểm kê mới")
    public ApiResponse<CycleCountResponse> create(@Valid @RequestBody CreateCycleCountRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Tạo phiếu kiểm kê thành công",
                cycleCountService.create(request, currentUserId(authentication)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Bắt đầu thực hiện kiểm kê")
    public ApiResponse<CycleCountResponse> start(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Bắt đầu kiểm kê thành công",
                cycleCountService.startCounting(id, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication),
                        staffOnly(authentication)));
    }

    @PostMapping("/{id}/record")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Nhập số lượng thực tế")
    public ApiResponse<CycleCountResponse> record(@PathVariable UUID id,
            @Valid @RequestBody RecordCountRequest request,
            Authentication authentication) {
        return ApiResponse.success("Ghi nhận số lượng thực tế thành công",
                cycleCountService.recordCount(id, request, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication),
                        staffOnly(authentication)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Gửi kết quả kiểm kê chờ duyệt")
    public ApiResponse<CycleCountResponse> submit(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Đã gửi kết quả kiểm kê, chờ duyệt",
                cycleCountService.submitForReview(id, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication),
                        staffOnly(authentication)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Alias cũ của gửi duyệt")
    public ApiResponse<CycleCountResponse> complete(@PathVariable UUID id, Authentication authentication) {
        return submit(id, authentication);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Duyệt kết quả kiểm kê và điều chỉnh tồn kho")
    public ApiResponse<CycleCountResponse> approve(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Đã duyệt và điều chỉnh tồn kho thành công",
                cycleCountService.approveAndAdjust(id, currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Từ chối kết quả và yêu cầu kiểm lại")
    public ApiResponse<CycleCountResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectCycleCountRequest request,
            Authentication authentication) {
        return ApiResponse.success("Đã yêu cầu kiểm kê lại",
                cycleCountService.rejectForRecount(id, request.reason(), currentUserId(authentication),
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Hủy phiếu kiểm kê")
    public ApiResponse<CycleCountResponse> cancel(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Đã hủy phiếu kiểm kê",
                cycleCountService.cancel(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private boolean staffOnly(Authentication authentication) {
        return hasAuthority(authentication, "WAREHOUSE_STAFF")
                && !hasAuthority(authentication, "ADMIN")
                && !hasAuthority(authentication, "WAREHOUSE_MANAGER");
    }

    private boolean reportOnly(Authentication authentication) {
        return hasAuthority(authentication, "REPORT_VIEWER")
                && !hasAuthority(authentication, "ADMIN")
                && !hasAuthority(authentication, "WAREHOUSE_MANAGER")
                && !hasAuthority(authentication, "WAREHOUSE_STAFF");
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }
}
