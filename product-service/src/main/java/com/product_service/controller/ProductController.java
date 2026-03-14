package com.product_service.controller;

import com.common.api.ApiResponse;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.service.ProductService;
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
@RequestMapping("/api/products")
@Tag(name = "Product APIs", description = "Quản lý sản phẩm trong hệ thống")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Lấy danh sách sản phẩm", description = "Trả về toàn bộ sản phẩm hiện có")
    public ApiResponse<List<ProductResponse>> getAll() {
        return ApiResponse.success("Lấy danh sách sản phẩm thành công", productService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy sản phẩm theo ID", description = "Trả về chi tiết sản phẩm theo UUID")
    public ApiResponse<ProductResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy sản phẩm thành công", productService.findById(id));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Lấy sản phẩm theo SKU", description = "Tìm sản phẩm bằng mã SKU")
    public ApiResponse<ProductResponse> getBySku(@PathVariable String sku) {
        return ApiResponse.success("Lấy sản phẩm thành công", productService.findBySku(sku));
    }

    @PostMapping
    @Operation(summary = "Tạo sản phẩm", description = "Tạo mới một sản phẩm")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success("Tạo sản phẩm thành công", productService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật sản phẩm", description = "Cập nhật thông tin sản phẩm theo ID")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success("Cập nhật sản phẩm thành công", productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm", description = "Xóa sản phẩm theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ApiResponse.success("Xóa sản phẩm thành công", id.toString());
    }
}