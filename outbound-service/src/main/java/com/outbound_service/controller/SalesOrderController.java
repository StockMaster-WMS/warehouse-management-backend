package com.outbound_service.controller;

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
@Tag(name = "Sales Order APIs", description = "Quản lý đơn xuất hàng")
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

    @PostMapping("/{id}/start-picking")
    @Operation(summary = "Bắt đầu picking", description = "PENDING → PICKING (cần có so-items)")
    public ApiResponse<SalesOrderResponse> startPicking(@PathVariable UUID id) {
        return ApiResponse.success("Chuyển sang picking thành công", salesOrderService.startPicking(id));
    }

    @PostMapping("/{id}/mark-picked")
    @Operation(summary = "Xác nhận đã pick xong", description = "PICKING → PICKED khi mọi picking line đã PICKED và đủ qty")
    public ApiResponse<SalesOrderResponse> markPicked(@PathVariable UUID id) {
        return ApiResponse.success("Xác nhận pick xong thành công", salesOrderService.markPicked(id));
    }

    @PostMapping("/{id}/mark-packed")
    @Operation(summary = "Đóng gói", description = "PICKED → PACKED")
    public ApiResponse<SalesOrderResponse> markPacked(@PathVariable UUID id) {
        return ApiResponse.success("Đóng gói thành công", salesOrderService.markPacked(id));
    }

    @PostMapping("/{id}/hold")
    @Operation(summary = "Tạm dừng đơn xuất", description = "PENDING/PICKING/PICKED/PACKED → ON_HOLD")
    public ApiResponse<SalesOrderResponse> hold(@PathVariable UUID id) {
        return ApiResponse.success("Tạm dừng đơn xuất thành công", salesOrderService.hold(id));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Tiếp tục đơn xuất", description = "ON_HOLD → PENDING hoặc PICKING hoặc PICKED (tùy tiến độ picking hiện tại)")
    public ApiResponse<SalesOrderResponse> resume(@PathVariable UUID id) {
        return ApiResponse.success("Tiếp tục đơn xuất thành công", salesOrderService.resume(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn xuất", description = "Hủy trước khi giao hàng; tự động nhả lượng đã giữ chỗ")
    public ApiResponse<SalesOrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.success("Hủy đơn xuất thành công", salesOrderService.cancel(id));
    }

    @PostMapping("/{id}/mark-shipped")
    @Operation(summary = "Giao hàng", description = "PACKED → SHIPPED, trừ tồn kho warehouse theo qty đã pick")
    public ApiResponse<SalesOrderResponse> markShipped(@PathVariable UUID id) {
        return ApiResponse.success("Xác nhận giao hàng thành công", salesOrderService.markShipped(id));
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
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }
}