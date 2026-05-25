package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.dto.request.BulkLocationGeneratorRequest;
import com.warehouse_service.dto.request.CreateLocationRequest;
import com.warehouse_service.dto.request.UpdateLocationRequest;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.service.LocationService;
import com.warehouse_service.service.WarehouseAccessService;
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
import org.springframework.security.core.Authentication;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locations")
@Tag(name = "Location ", description = "Quản lý vị trí lưu trữ trong kho")
public class LocationController {

    private final LocationService locationService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách vị trí", description = "Phân trang; lọc kho, zone, keyword (mã/zone/aisle)")
    public ApiResponse<PagedResponse<LocationResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "ID kho") @RequestParam(required = false) UUID warehouseId,
            @Parameter(description = "Zone") @RequestParam(required = false) String zone,
            @Parameter(description = "Loại vị trí") @RequestParam(required = false) String locationType,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        warehouseAccessService.assertCanAccessWarehouse(authentication, warehouseId);
        return ApiResponse.success("Lấy danh sách vị trí thành công",
                locationService.findAll(pageable, warehouseId, zone, locationType, keyword,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy vị trí theo ID")
    public ApiResponse<LocationResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy vị trí thành công",
                locationService.findById(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo vị trí")
    public ApiResponse<LocationResponse> create(@Valid @RequestBody CreateLocationRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Tạo vị trí thành công", locationService.create(request));
    }

    @PostMapping("/bulk-generate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo hàng loạt vị trí theo quy luật")
    public ApiResponse<String> bulkGenerate(@Valid @RequestBody BulkLocationGeneratorRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.getWarehouseId());
        locationService.generateBulk(request);
        return ApiResponse.success("Bắt đầu tiến trình tạo hàng loạt vị trí thành công", "SUCCESS");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Cập nhật vị trí")
    public ApiResponse<LocationResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request,
            Authentication authentication) {
        warehouseAccessService.assertCanAccessWarehouse(authentication, request.warehouseId());
        return ApiResponse.success("Cập nhật vị trí thành công", locationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Xóa vị trí")
    public ApiResponse<String> delete(@PathVariable UUID id, Authentication authentication) {
        locationService.findById(id, warehouseAccessService.visibleWarehouseIdSet(authentication));
        locationService.delete(id);
        return ApiResponse.success("Xóa vị trí thành công", id.toString());
    }
}
