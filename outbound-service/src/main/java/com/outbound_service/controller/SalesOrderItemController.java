package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.outbound_service.dto.request.CreateSalesOrderItemRequest;
import com.outbound_service.dto.request.UpdateSalesOrderItemRequest;
import com.outbound_service.dto.response.SalesOrderItemResponse;
import com.outbound_service.service.SalesOrderItemService;
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
@RequestMapping("/api/so-items")
@Tag(name = "Sales order line APIs", description = "Dòng đơn xuất (SO items)")
public class SalesOrderItemController {

    private final SalesOrderItemService salesOrderItemService;

    @GetMapping
    @Operation(summary = "Danh sách dòng đơn xuất", description = "Lọc theo salesOrderId")
    public ApiResponse<List<SalesOrderItemResponse>> getAll(
            @Parameter(description = "ID đơn xuất")
            @RequestParam(required = false) UUID salesOrderId) {
        return ApiResponse.success("Lấy danh sách dòng đơn xuất thành công",
                salesOrderItemService.findAll(salesOrderId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết dòng đơn xuất")
    public ApiResponse<SalesOrderItemResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy dòng đơn xuất thành công", salesOrderItemService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo dòng đơn xuất")
    public ApiResponse<SalesOrderItemResponse> create(@Valid @RequestBody CreateSalesOrderItemRequest request) {
        return ApiResponse.success("Tạo dòng đơn xuất thành công", salesOrderItemService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật dòng đơn xuất")
    public ApiResponse<SalesOrderItemResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateSalesOrderItemRequest request) {
        return ApiResponse.success("Cập nhật dòng đơn xuất thành công", salesOrderItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa dòng đơn xuất")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        salesOrderItemService.delete(id);
        return ApiResponse.success("Xóa dòng đơn xuất thành công", id.toString());
    }
}
