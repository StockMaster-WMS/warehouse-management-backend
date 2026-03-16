package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.service.PurchaseOrderService;
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
@RequestMapping("/api/purchase-orders")
@Tag(name = "Purchase Order APIs", description = "Quản lý đơn nhập hàng")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @Operation(summary = "Lấy danh sách đơn nhập", description = "Trả về toàn bộ purchase order")
    public ApiResponse<List<PurchaseOrderResponse>> getAll() {
        return ApiResponse.success("Lấy danh sách đơn nhập thành công", purchaseOrderService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy đơn nhập theo ID", description = "Trả về chi tiết purchase order theo UUID")
    public ApiResponse<PurchaseOrderResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy đơn nhập thành công", purchaseOrderService.findById(id));
    }

    @GetMapping("/number/{poNumber}")
    @Operation(summary = "Lấy đơn nhập theo mã", description = "Tìm purchase order bằng poNumber")
    public ApiResponse<PurchaseOrderResponse> getByPoNumber(@PathVariable String poNumber) {
        return ApiResponse.success("Lấy đơn nhập thành công", purchaseOrderService.findByPoNumber(poNumber));
    }

    @PostMapping
    @Operation(summary = "Tạo đơn nhập", description = "Tạo mới một purchase order")
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ApiResponse.success("Tạo đơn nhập thành công", purchaseOrderService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật đơn nhập", description = "Cập nhật purchase order theo ID")
    public ApiResponse<PurchaseOrderResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseOrderRequest request) {
        return ApiResponse.success("Cập nhật đơn nhập thành công", purchaseOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn nhập", description = "Xóa purchase order theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        purchaseOrderService.delete(id);
        return ApiResponse.success("Xóa đơn nhập thành công", id.toString());
    }
}