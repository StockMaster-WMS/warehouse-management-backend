package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.inbound_service.dto.request.CreateInboundReceiptRequest;
import com.inbound_service.dto.response.InboundLocationSuggestionResponse;
import com.inbound_service.dto.response.InboundPrintResponse;
import com.inbound_service.dto.response.InboundReceiptResponse;
import com.inbound_service.entity.InboundReceiptStatus;
import com.inbound_service.service.InboundReceiptExcelExportService;
import com.inbound_service.service.InboundReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inbound-receipts")
@Tag(name = "Inbound Receipt ", description = "Phiếu nhập kho – nhận hàng từ PO")
public class InboundReceiptController {

    private final InboundReceiptService receiptService;
    private final InboundReceiptExcelExportService receiptExcelExportService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Danh sách phiếu nhập kho", description = "Phân trang; lọc keyword, purchaseOrderId, warehouseId, status")
    public ApiResponse<PagedResponse<InboundReceiptResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.util.UUID purchaseOrderId,
            @RequestParam(required = false) java.util.UUID warehouseId,
            @RequestParam(required = false) InboundReceiptStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách phiếu nhập thành công",
                receiptService.findAll(pageable, keyword, purchaseOrderId, warehouseId, status, createdFrom, createdTo));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo phiếu nhập kho", description = "Nhận hàng theo PO: kiểm tra số lượng → tạo phiếu → cập nhật tồn kho → cập nhật trạng thái PO")
    public ApiResponse<InboundReceiptResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateInboundReceiptRequest request) {
        return ApiResponse.success("Tạo phiếu nhập kho thành công",
                receiptService.createReceipt(request, idempotencyKey));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy phiếu nhập theo ID")
    public ApiResponse<InboundReceiptResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy phiếu nhập thành công", receiptService.findById(id));
    }

    @GetMapping("/{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy thông tin in phiếu nhập kho", description = "Trả về dữ liệu chi tiết của phiếu nhập gồm cả thông tin NCC và Tên SP để in ấn")
    public ApiResponse<InboundPrintResponse> getPrintData(@PathVariable UUID id) {
        return ApiResponse.success("Lấy thông tin in phiếu nhập kho thành công", receiptService.getPrintData(id));
    }

    @GetMapping("/by-po/{purchaseOrderId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lấy danh sách phiếu nhập theo PO", description = "Liệt kê tất cả phiếu nhập của một đơn mua hàng")
    public ApiResponse<List<InboundReceiptResponse>> getByPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return ApiResponse.success("Lấy danh sách phiếu nhập thành công",
                receiptService.findByPurchaseOrderId(purchaseOrderId));
    }

    @GetMapping("/location-suggestions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Goi y vi tri nhap hang theo san pham", description = "Uu tien vi tri cu cua san pham trong kho, sau do toi vi tri trong.")
    public ApiResponse<List<InboundLocationSuggestionResponse>> suggestLocations(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID poItemId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success("Goi y vi tri nhap hang thanh cong",
                receiptService.suggestLocations(warehouseId, productId, poItemId, limit));
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Xuất phiếu nhập kho ra Excel", description = "Xuất danh sách phiếu nhập theo bộ lọc hiện có")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) InboundReceiptStatus status) {
        byte[] bytes = receiptExcelExportService.exportToXlsx(keyword, purchaseOrderId, warehouseId, status);
        String filename = "inbound-receipts-export-" + java.time.LocalDate.now() + ".xlsx";
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
