package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.service.PickingItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/picking-items")
@Tag(name = "Picking Item ", description = "Quản lý item picking nội bộ")
public class PickingItemController {

    private final PickingItemService pickingItemService;

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
            @Parameter(description = "Trạng thái (PENDING, PICKED, ...)") @RequestParam(required = false) String status) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách picking item thành công",
                pickingItemService.findAll(pageable, soItemId, productId, locationId, status));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Phân công nhiệm vụ lấy hàng")
    public ApiResponse<PickingItemResponse> assignTask(@PathVariable UUID id, @RequestBody java.util.Map<String, String> payload) {
        UUID assigneeId = payload.get("assigneeId") != null ? UUID.fromString(payload.get("assigneeId")) : null;
        return ApiResponse.success("Phân công thành công", pickingItemService.assignTask(id, assigneeId));
    }

    @PostMapping("/{id}/exception")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Báo lỗi lấy hàng")
    public ApiResponse<PickingItemResponse> reportException(@PathVariable UUID id, @RequestBody java.util.Map<String, String> payload) {
        String reason = payload.get("reason");
        return ApiResponse.success("Ghi nhận báo lỗi thành công", pickingItemService.reportException(id, reason));
    }

    @PostMapping("/{id}/complete-mobile")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Hoàn tất picking nhanh cho Mobile", description = "Dùng cho Mobile khi quét xong: Tự động set status=PICKED và cập nhật kho")
    public ApiResponse<PickingItemResponse> completeMobile(@PathVariable UUID id) {
        return ApiResponse.success("Hoàn tất lấy hàng thành công", pickingItemService.completeMobile(id));
    }

    private String resolveSortField(String sort) {
        if ("salesOrderNumber".equalsIgnoreCase(sort)) {
            return "soItem.salesOrder.soNumber";
        }
        return sort;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy picking item theo ID")
    public ApiResponse<?> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy chi tiết picking item thành công", pickingItemService.findDetailForPicker(id));
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