package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.outbound_service.dto.request.CreateSalesOrderItemRequest;
import com.outbound_service.dto.request.UpdateSalesOrderItemRequest;
import com.outbound_service.dto.response.SalesOrderItemResponse;
import com.outbound_service.service.SalesOrderItemService;
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
import com.warehouse_service.service.WarehouseAccessService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/so-items")
@Tag(name = "Sales order line ", description = "Dòng đơn xuất (SO items)")
public class SalesOrderItemController {

    private final SalesOrderItemService salesOrderItemService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Danh sách dòng đơn xuất", description = "Phân trang; lọc salesOrderId, keyword (SKU)")
    public ApiResponse<PagedResponse<SalesOrderItemResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lineNumber") String sort,
            @RequestParam(defaultValue = "asc") String sortDir,
            @Parameter(description = "ID đơn xuất") @RequestParam(required = false) UUID salesOrderId,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách dòng đơn xuất thành công",
                salesOrderItemService.findAll(pageable, salesOrderId, keyword,
                        warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Chi tiết dòng đơn xuất")
    public ApiResponse<SalesOrderItemResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Lấy dòng đơn xuất thành công",
                salesOrderItemService.findById(id, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo dòng đơn xuất")
    public ApiResponse<SalesOrderItemResponse> create(@Valid @RequestBody CreateSalesOrderItemRequest request,
            Authentication authentication) {
        return ApiResponse.success("Tạo dòng đơn xuất thành công", salesOrderItemService.create(request, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Cập nhật dòng đơn xuất")
    public ApiResponse<SalesOrderItemResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateSalesOrderItemRequest request,
            Authentication authentication) {
        return ApiResponse.success("Cập nhật dòng đơn xuất thành công",
                salesOrderItemService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Xóa dòng đơn xuất")
    public ApiResponse<String> delete(@PathVariable UUID id, Authentication authentication) {
        salesOrderItemService.delete(id, authentication);
        return ApiResponse.success("Xóa dòng đơn xuất thành công", id.toString());
    }
}
