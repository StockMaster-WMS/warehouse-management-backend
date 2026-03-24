package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.dto.request.CreateLocationRequest;
import com.warehouse_service.dto.request.UpdateLocationRequest;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.service.LocationService;
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
@RequestMapping("/api/locations")
@Tag(name = "Location APIs", description = "Quản lý vị trí lưu trữ trong kho")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @Operation(summary = "Lấy danh sách vị trí", description = "Phân trang; lọc kho, zone, keyword (mã/zone/aisle)")
    public ApiResponse<PagedResponse<LocationResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "ID kho")
            @RequestParam(required = false) UUID warehouseId,
            @Parameter(description = "Zone")
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách vị trí thành công",
                locationService.findAll(pageable, warehouseId, zone, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy vị trí theo ID")
    public ApiResponse<LocationResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy vị trí thành công", locationService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo vị trí")
    public ApiResponse<LocationResponse> create(@Valid @RequestBody CreateLocationRequest request) {
        return ApiResponse.success("Tạo vị trí thành công", locationService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật vị trí")
    public ApiResponse<LocationResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateLocationRequest request) {
        return ApiResponse.success("Cập nhật vị trí thành công", locationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa vị trí")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        locationService.delete(id);
        return ApiResponse.success("Xóa vị trí thành công", id.toString());
    }
}