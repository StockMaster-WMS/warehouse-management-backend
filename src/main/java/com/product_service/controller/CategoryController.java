package com.product_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.product_service.dto.request.CreateCategoryRequest;
import com.product_service.dto.request.UpdateCategoryRequest;
import com.product_service.dto.response.CategoryResponse;
import com.product_service.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/categories")
@Tag(name = "Category ", description = "Quản lý danh mục trong hệ thống")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách danh mục", description = "Phân trang; lọc keyword (mã, tên, path) và isActive")
    public ApiResponse<PagedResponse<CategoryResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách danh mục thành công",
                categoryService.findAll(pageable, keyword, isActive));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy danh mục theo ID", description = "Trả về chi tiết danh mục theo UUID")
    public ApiResponse<CategoryResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy danh mục thành công", categoryService.findById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Lấy danh mục theo mã", description = "Tìm danh mục bằng mã code")
    public ApiResponse<CategoryResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success("Lấy danh mục thành công", categoryService.findByCode(code));
    }

    @PostMapping
    @Operation(summary = "Tạo danh mục", description = "Tạo mới một danh mục")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success("Tạo danh mục thành công", categoryService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật danh mục", description = "Cập nhật thông tin danh mục theo ID")
    public ApiResponse<CategoryResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success("Cập nhật danh mục thành công", categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa danh mục", description = "Xóa danh mục theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ApiResponse.success("Xóa danh mục thành công", id.toString());
    }
}
