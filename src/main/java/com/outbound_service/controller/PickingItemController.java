package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.service.PickingItemService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/picking-items")
@Tag(name = "Picking Item ", description = "Quản lý item picking nội bộ")
public class PickingItemController {

    private final PickingItemService pickingItemService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách picking items", description = "Phân trang; lọc soItemId, productId, locationId, status")
    public ApiResponse<PagedResponse<PickingItemResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "pickSequence") String sort,
            @RequestParam(defaultValue = "asc") String sortDir,
            @Parameter(description = "ID so item") @RequestParam(required = false) UUID soItemId,
            @Parameter(description = "ID sản phẩm") @RequestParam(required = false) UUID productId,
            @Parameter(description = "ID vị trí") @RequestParam(required = false) UUID locationId,
            @Parameter(description = "Trạng thái picking") @RequestParam(required = false) String status,
            @Parameter(description = "Trạng thái đơn xuất") @RequestParam(required = false) String salesOrderStatus,
            @Parameter(description = "Từ thời điểm tạo đơn xuất (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @Parameter(description = "Đến thời điểm tạo đơn xuất (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            Authentication authentication) {
        String resolvedSort = resolveSortField(sort);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), resolvedSort));
        return ApiResponse.success("Lấy danh sách picking item thành công",
                pickingItemService.findAll(pageable, soItemId, productId, locationId, status, salesOrderStatus, createdFrom, createdTo,
                        staffScopeUserId(authentication), warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Phân công nhiệm vụ lấy hàng")
    public ApiResponse<PickingItemResponse> assignTask(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        UUID assigneeId = payload.get("assigneeId") != null ? UUID.fromString(payload.get("assigneeId")) : null;
        return ApiResponse.success("Phân công thành công", pickingItemService.assignTask(id, assigneeId));
    }

    @PostMapping("/{id}/exception")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Báo lỗi lấy hàng")
    public ApiResponse<PickingItemResponse> reportException(@PathVariable UUID id, @RequestBody Map<String, String> payload,
            Authentication authentication) {
        String reason = payload.get("reason");
        return ApiResponse.success("Ghi nhận báo lỗi thành công",
                pickingItemService.reportException(id, reason, currentUserId(authentication), canBypassTaskScope(authentication)));
    }

    @PostMapping("/{id}/complete-mobile")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Hoàn tất picking nhanh cho Mobile", description = "Dùng cho Mobile khi quét xong: Tự động set status=PICKED và cập nhật kho")
    public ApiResponse<PickingItemResponse> completeMobile(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Hoàn tất lấy hàng thành công",
                pickingItemService.completeMobile(id, currentUserId(authentication), canBypassTaskScope(authentication)));
    }

    private String resolveSortField(String sort) {
        if ("salesOrderNumber".equalsIgnoreCase(sort)) {
            return "soItem.salesOrder.soNumber";
        }
        return sort;
    }

    private UUID staffScopeUserId(Authentication authentication) {
        return canBypassTaskScope(authentication) ? null : currentUserId(authentication);
    }

    private boolean canBypassTaskScope(Authentication authentication) {
        return hasAuthority(authentication, "ADMIN") || hasAuthority(authentication, "WAREHOUSE_MANAGER");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy picking item theo ID")
    public ApiResponse<?> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy chi tiết picking item thành công",
                pickingItemService.findDetailForPicker(id, currentUserId(authentication), canBypassTaskScope(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo picking item")
    public ApiResponse<PickingItemResponse> create(@Valid @RequestBody CreatePickingItemRequest request) {
        return ApiResponse.success("Tạo picking item thành công", pickingItemService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Cập nhật picking item")
    public ApiResponse<PickingItemResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePickingItemRequest request) {
        return ApiResponse.success("Cập nhật picking item thành công", pickingItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Xóa picking item")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        pickingItemService.delete(id);
        return ApiResponse.success("Xóa picking item thành công", id.toString());
    }
}
