package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/warehouses")
@Tag(name = "Warehouse APIs", description = "Quản lý kho hàng")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "Lấy danh sách kho", description = "Trả về toàn bộ kho trong hệ thống")
    public ApiResponse<List<WarehouseResponse>> getAll() {
        return ApiResponse.success("Lấy danh sách kho thành công", warehouseService.findAll());
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