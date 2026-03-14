package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.inbound_service.dto.request.CreatePoItemRequest;
import com.inbound_service.dto.request.UpdatePoItemRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.service.PoItemService;
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
@RequestMapping("/api/po-items")
@Tag(name = "PO Item APIs", description = "Quản lý dòng đơn nhập nội bộ")
public class PoItemController {

    private final PoItemService poItemService;

    @GetMapping
    @Operation(summary = "Lấy danh sách dòng đơn nhập", description = "Có thể lọc theo purchaseOrderId")
    public ApiResponse<List<PoItemResponse>> getAll(
            @Parameter(description = "ID đơn nhập")
            @RequestParam(required = false) UUID purchaseOrderId) {
        return ApiResponse.success("Lấy danh sách dòng đơn nhập thành công", poItemService.findAll(purchaseOrderId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy dòng đơn nhập theo ID")
    public ApiResponse<PoItemResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy dòng đơn nhập thành công", poItemService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo dòng đơn nhập")
    public ApiResponse<PoItemResponse> create(@Valid @RequestBody CreatePoItemRequest request) {
        return ApiResponse.success("Tạo dòng đơn nhập thành công", poItemService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật dòng đơn nhập")
    public ApiResponse<PoItemResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdatePoItemRequest request) {
        return ApiResponse.success("Cập nhật dòng đơn nhập thành công", poItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa dòng đơn nhập")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        poItemService.delete(id);
        return ApiResponse.success("Xóa dòng đơn nhập thành công", id.toString());
    }
}