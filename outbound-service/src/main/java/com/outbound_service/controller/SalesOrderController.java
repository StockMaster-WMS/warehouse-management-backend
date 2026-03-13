package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.service.SalesOrderService;
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
@RequestMapping("/api/sales-orders")
@Tag(name = "Sales Order APIs", description = "Quản lý đơn xuất hàng")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    @GetMapping
    @Operation(summary = "Lấy danh sách đơn xuất", description = "Trả về toàn bộ sales order")
    public ApiResponse<List<SalesOrderResponse>> getAll() {
        return ApiResponse.success("Fetched sales orders successfully", salesOrderService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy đơn xuất theo ID", description = "Trả về chi tiết sales order theo UUID")
    public ApiResponse<SalesOrderResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Fetched sales order successfully", salesOrderService.findById(id));
    }

    @GetMapping("/number/{soNumber}")
    @Operation(summary = "Lấy đơn xuất theo mã", description = "Tìm sales order bằng soNumber")
    public ApiResponse<SalesOrderResponse> getBySoNumber(@PathVariable String soNumber) {
        return ApiResponse.success("Fetched sales order successfully", salesOrderService.findBySoNumber(soNumber));
    }

    @PostMapping
    @Operation(summary = "Tạo đơn xuất", description = "Tạo mới một sales order")
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody CreateSalesOrderRequest request) {
        return ApiResponse.success("Created sales order successfully", salesOrderService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật đơn xuất", description = "Cập nhật sales order theo ID")
    public ApiResponse<SalesOrderResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateSalesOrderRequest request) {
        return ApiResponse.success("Updated sales order successfully", salesOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn xuất", description = "Xóa sales order theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        salesOrderService.delete(id);
        return ApiResponse.success("Deleted sales order successfully", id.toString());
    }
}