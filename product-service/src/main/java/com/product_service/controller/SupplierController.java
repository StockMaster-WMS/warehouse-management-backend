package com.product_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.product_service.dto.request.CreateSupplierRequest;
import com.product_service.dto.request.UpdateSupplierRequest;
import com.product_service.dto.response.SupplierResponse;
import com.product_service.service.SupplierExcelExportService;
import com.product_service.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/suppliers")
@Tag(name = "Supplier APIs", description = "Quản lý nhà cung cấp trong hệ thống")
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierExcelExportService supplierExcelExportService;

    @GetMapping
    @Operation(summary = "Lấy danh sách nhà cung cấp",
            description = "Phân trang; lọc theo keyword (mã, tên, MST, người liên hệ) và trạng thái")
    public ApiResponse<PagedResponse<SupplierResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        PagedResponse<SupplierResponse> paged = supplierService.findAll(pageable, keyword, status);
        return ApiResponse.success("Lấy danh sách nhà cung cấp thành công", paged);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy nhà cung cấp theo ID", description = "Trả về chi tiết nhà cung cấp theo UUID")
    public ApiResponse<SupplierResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy nhà cung cấp thành công", supplierService.findById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Lấy nhà cung cấp theo mã", description = "Tìm nhà cung cấp bằng mã code")
    public ApiResponse<SupplierResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success("Lấy nhà cung cấp thành công", supplierService.findByCode(code));
    }

    @PostMapping
    @Operation(summary = "Tạo nhà cung cấp", description = "Tạo mới một nhà cung cấp")
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        return ApiResponse.success("Tạo nhà cung cấp thành công", supplierService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật nhà cung cấp", description = "Cập nhật thông tin nhà cung cấp theo ID")
    public ApiResponse<SupplierResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateSupplierRequest request) {
        return ApiResponse.success("Cập nhật nhà cung cấp thành công", supplierService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Thay đổi trạng thái nhà cung cấp", description = "Đổi trạng thái: ACTIVE, INACTIVE, SUSPENDED")
    public ApiResponse<SupplierResponse> changeStatus(@PathVariable UUID id,
                                                      @RequestParam String status) {
        return ApiResponse.success("Cập nhật trạng thái thành công", supplierService.changeStatus(id, status));
    }

    @GetMapping("/export")
    @Operation(summary = "Xuất Excel danh sách NCC", description = "Download file .xlsx theo bộ lọc hiện tại")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        byte[] bytes = supplierExcelExportService.exportToXlsx(keyword, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=suppliers.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhà cung cấp", description = "Xóa nhà cung cấp theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        supplierService.delete(id);
        return ApiResponse.success("Xóa nhà cung cấp thành công", id.toString());
    }
}
