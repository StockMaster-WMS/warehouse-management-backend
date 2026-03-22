package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.dto.response.WarehouseSummaryResponse;
import com.warehouse_service.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/warehouses")
@Tag(name = "Warehouse APIs", description = "Quản lý kho hàng")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/summary")
    @Operation(summary = "Tổng quan kho", description = "Trả về số liệu tổng quan phục vụ dashboard kho")
    public ApiResponse<WarehouseSummaryResponse> getSummary() {
        return ApiResponse.success("Lấy tổng quan kho thành công", warehouseService.getSummary());
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách kho", description = "Trả về danh sách kho hỗ trợ phân trang và tìm kiếm")
    public ApiResponse<PagedResponse<WarehouseResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String timezone) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        PagedResponse<WarehouseResponse> pagedResponse = warehouseService.findAll(pageable, keyword, isActive, timezone);
        return ApiResponse.success("Lấy danh sách kho thành công", pagedResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy kho theo ID", description = "Trả về chi tiết kho theo UUID")
    public ApiResponse<WarehouseResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy kho thành công", warehouseService.findById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Lấy kho theo mã", description = "Tìm kho bằng mã code")
    public ApiResponse<WarehouseResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success("Lấy kho thành công", warehouseService.findByCode(code));
    }

    @PostMapping
    @Operation(summary = "Tạo kho", description = "Tạo mới một kho")
    public ApiResponse<WarehouseResponse> create(@Valid @RequestBody CreateWarehouseRequest request) {
        return ApiResponse.success("Tạo kho thành công", warehouseService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật kho", description = "Cập nhật thông tin kho theo ID")
    public ApiResponse<WarehouseResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ApiResponse.success("Cập nhật kho thành công", warehouseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa kho", description = "Xóa kho theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ApiResponse.success("Xóa kho thành công", id.toString());
    }
}