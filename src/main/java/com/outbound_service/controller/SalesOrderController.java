package com.outbound_service.controller;

import com.outbound_service.dto.request.SalesOrderActionRequest;
import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.service.SalesOrderExcelExportService;
import com.outbound_service.service.SalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sales-orders")
@Tag(name = "Sales Order ", description = "Quản lý đơn xuất hàng")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final SalesOrderExcelExportService salesOrderExcelExportService;

    @GetMapping
    @Operation(summary = "Lấy danh sách đơn xuất", description = "Phân trang; lọc keyword, status, warehouseId")
    public ApiResponse<PagedResponse<SalesOrderResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách đơn xuất thành công",
                salesOrderService.findAll(pageable, keyword, status, warehouseId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy đơn xuất theo ID", description = "Trả về chi tiết sales order theo UUID")
    public ApiResponse<SalesOrderResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy đơn xuất thành công", salesOrderService.findById(id));
    }

    @GetMapping("/number/{soNumber}")
    @Operation(summary = "Lấy đơn xuất theo mã", description = "Tìm sales order bằng soNumber")
    public ApiResponse<SalesOrderResponse> getBySoNumber(@PathVariable String soNumber) {
        return ApiResponse.success("Lấy đơn xuất thành công", salesOrderService.findBySoNumber(soNumber));
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "Thực thi hành động trên đơn xuất", description = "Dùng một endpoint duy nhất để chuyển trạng thái đơn hàng (confirm, start-picking, mark-picked, mark-packed, hold, resume, cancel, mark-shipped, mark-delivered)")
    public ApiResponse<SalesOrderResponse> executeAction(@PathVariable UUID id,
            @Valid @RequestBody SalesOrderActionRequest request) {
        return ApiResponse.success("Thực hiện hành động thành công", salesOrderService.executeAction(id, request));
    }

    @PostMapping
    @Operation(summary = "Tạo đơn xuất", description = "Tạo mới sales order; mã đơn (soNumber) tự sinh bởi hệ thống")
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody CreateSalesOrderRequest request) {
        return ApiResponse.success("Tạo đơn xuất thành công", salesOrderService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật đơn xuất", description = "Cập nhật sales order theo ID")
    public ApiResponse<SalesOrderResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateSalesOrderRequest request) {
        return ApiResponse.success("Cập nhật đơn xuất thành công", salesOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn xuất", description = "Xóa sales order theo ID")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        salesOrderService.delete(id);
        return ApiResponse.success("Xóa đơn xuất thành công", id.toString());
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Xuất phiếu xuất kho ra Excel", description = "Xuất danh sách sales order cùng chi tiết hàng hóa")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId) {
        byte[] bytes = salesOrderExcelExportService.exportToXlsx(keyword, status, warehouseId);
        String filename = "sales-orders-export-" + java.time.LocalDate.now() + ".xlsx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }
}