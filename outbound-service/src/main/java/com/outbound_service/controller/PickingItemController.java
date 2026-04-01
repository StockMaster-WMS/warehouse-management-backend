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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/picking-items")
@Tag(name = "Picking Item APIs", description = "Quản lý item picking nội bộ")
public class PickingItemController {

    private final PickingItemService pickingItemService;

    @GetMapping
    @Operation(summary = "Lấy danh sách picking items", description = "Phân trang; lọc soItemId, productId, locationId")
    public ApiResponse<PagedResponse<PickingItemResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "ID so item") @RequestParam(required = false) UUID soItemId,
            @Parameter(description = "ID sản phẩm") @RequestParam(required = false) UUID productId,
            @Parameter(description = "ID vị trí") @RequestParam(required = false) UUID locationId) {
        String resolvedSort = resolveSortField(sort);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), resolvedSort));
        return ApiResponse.success("Lấy danh sách picking item thành công",
                pickingItemService.findAll(pageable, soItemId, productId, locationId));
    }

    private String resolveSortField(String sort) {
        if ("salesOrderNumber".equalsIgnoreCase(sort)) {
            return "soItem.salesOrder.soNumber";
        }
        return sort;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy picking item theo ID", description = "Thêm details=true để lấy đầy đủ thông tin sản phẩm, vị trí, tồn khả dụng")
    public ApiResponse<?> getById(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean details) {
        if (details) {
            return ApiResponse.success("Lấy chi tiết picking item thành công", pickingItemService.findDetailForPicker(id));
        } else {
            return ApiResponse.success("Lấy picking item thành công", pickingItemService.findById(id));
        }
    }

    @PostMapping
    @Operation(summary = "Tạo picking item")
    public ApiResponse<PickingItemResponse> create(@Valid @RequestBody CreatePickingItemRequest request) {
        return ApiResponse.success("Tạo picking item thành công", pickingItemService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật picking item")
    public ApiResponse<PickingItemResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePickingItemRequest request) {
        return ApiResponse.success("Cập nhật picking item thành công", pickingItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa picking item")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        pickingItemService.delete(id);
        return ApiResponse.success("Xóa picking item thành công", id.toString());
    }
}