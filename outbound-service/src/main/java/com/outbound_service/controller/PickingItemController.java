package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.service.PickingItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/picking-items")
@Tag(name = "Picking Item APIs", description = "Quản lý item picking nội bộ")
public class PickingItemController {

    private final PickingItemService pickingItemService;

    @GetMapping
    @Operation(summary = "Lấy danh sách picking items", description = "Lọc theo soItemId, productId hoặc locationId")
    public ApiResponse<List<PickingItemResponse>> getAll(
            @Parameter(description = "ID so item")
            @RequestParam(required = false) UUID soItemId,
            @Parameter(description = "ID sản phẩm")
            @RequestParam(required = false) UUID productId,
            @Parameter(description = "ID vị trí")
            @RequestParam(required = false) UUID locationId) {
        return ApiResponse.success("Lấy danh sách picking item thành công",
                pickingItemService.findAll(soItemId, productId, locationId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy picking item theo ID")
    public ApiResponse<PickingItemResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy picking item thành công", pickingItemService.findById(id));
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