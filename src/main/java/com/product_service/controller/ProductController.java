package com.product_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.dto.response.ProductImportResponse;
import com.product_service.dto.response.ProductResponse;
import com.product_service.dto.response.ProductSummaryResponse;
import com.product_service.service.ProductExcelExportService;
import com.product_service.service.ProductExcelImportService;
import com.product_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
@Tag(name = "Product ", description = "Quản lý sản phẩm trong hệ thống")
public class ProductController {

    private final ProductService productService;
    private final ProductExcelImportService productExcelImportService;
    private final ProductExcelExportService productExcelExportService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách sản phẩm", description = "Trả về danh sách sản phẩm hỗ trợ phân trang và tìm kiếm")
    public ApiResponse<PagedResponse<ProductResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        PagedResponse<ProductResponse> pagedResponse = productService.findAll(pageable, keyword, categoryId, status);
        return ApiResponse.success("Lấy danh sách sản phẩm thành công", pagedResponse);
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Xuất danh sách sản phẩm ra Excel (.xlsx)", description = "Cùng bộ lọc với danh sách; tối đa 10.000 dòng. Cột tương thích import (bỏ qua sku, id khi nhập mới).")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status) {
        byte[] bytes = productExcelExportService.exportToXlsx(keyword, categoryId, status);
        String filename = "products-export-" + LocalDate.now() + ".xlsx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy sản phẩm theo ID", description = "Trả về chi tiết sản phẩm theo UUID")
    public ApiResponse<ProductResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy sản phẩm thành công", productService.findById(id));
    }

    @GetMapping("/sku/{sku}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy sản phẩm theo SKU", description = "Tìm sản phẩm bằng mã SKU")
    public ApiResponse<ProductResponse> getBySku(@PathVariable String sku) {
        return ApiResponse.success("Lấy sản phẩm thành công", productService.findBySku(sku));
    }

    @GetMapping("/find-by-name")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tìm sản phẩm theo tên", description = "Tìm chính xác theo tên (không phân biệt hoa/thường)")
    public ApiResponse<ProductResponse> findByName(@RequestParam String name) {
        return ApiResponse.success("Lấy sản phẩm thành công", productService.findByName(name));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo sản phẩm", description = "Tạo mới sản phẩm; mã SKU do hệ thống tự sinh (tiền tố SP)")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success("Tạo sản phẩm thành công", productService.create(request));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách sản phẩm theo ID (batch)", description = "Dùng cho các service khác (vd tồn kho) lấy thông tin gọn theo nhiều productId trong 1 call.")
    public ApiResponse<List<ProductSummaryResponse>> getByIds(@RequestBody List<UUID> ids) {
        return ApiResponse.success("Lấy danh sách sản phẩm thành công", productService.findSummariesByIds(ids));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Import sản phẩm từ Excel (.xlsx)")
    public ApiResponse<ProductImportResponse> importXlsx(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "UUID người tạo; bỏ trống = import hệ thống") @RequestParam(required = false) UUID createdBy) {
        ProductImportResponse result = productExcelImportService.importFromXlsx(file, createdBy);
        return ApiResponse.success("Import hoàn tất", result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Cập nhật sản phẩm", description = "Cập nhật thông tin sản phẩm theo ID")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success("Cập nhật sản phẩm thành công", productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Xóa sản phẩm", description = "Xóa sản phẩm theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ApiResponse.success("Xóa sản phẩm thành công", id.toString());
    }
}
