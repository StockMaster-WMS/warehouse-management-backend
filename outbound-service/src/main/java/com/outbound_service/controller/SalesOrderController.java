package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    @GetMapping
    public ApiResponse<List<SalesOrderResponse>> getAll() {
        return ApiResponse.success("Fetched sales orders successfully", salesOrderService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SalesOrderResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Fetched sales order successfully", salesOrderService.findById(id));
    }

    @PostMapping
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody CreateSalesOrderRequest request) {
        return ApiResponse.success("Created sales order successfully", salesOrderService.create(request));
    }
}