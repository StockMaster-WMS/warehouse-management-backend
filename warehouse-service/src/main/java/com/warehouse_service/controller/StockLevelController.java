package com.warehouse_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.warehouse_service.dto.request.CreateStockLevelRequest;
import com.warehouse_service.dto.request.UpdateStockLevelRequest;
import com.warehouse_service.dto.response.StockLevelExpandedResponse;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.warehouse_service.dto.response.NearExpiryStockResponse;
import com.warehouse_service.dto.response.StockSummaryResponse;
import com.warehouse_service.service.StockLevelExcelExportService;
import com.warehouse_service.service.StockLevelService;
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
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
@Tag(name = "Stock APIs", description = "Quản lý tồn kho nội bộ theo vị trí")
public class StockLevelController {

    private final StockLevelService stockLevelService;
    private final StockLevelExcelExportService stockLevelExcelExportService;

    @GetMapping("/summary")
    @Operation(summary = "Tổng quan tồn kho", description = "Trả về số liệu tổng quan: tổng SKU, tổng tồn, tồn thấp, sắp hết hạn")
    public ApiResponse<StockSummaryResponse> getSummary(
            @Parameter(description = "Số ngày tính sắp hết hạn") @RequestParam(defaultValue = "30") int nearExpiryDays) {
        return ApiResponse.success("Lấy tổng quan tồn kho thành công", stockLevelService.getSummary(nearExpiryDays));
    }

    @GetMapping("/alerts/low-stock")
    @Operation(summary = "Cảnh báo tồn kho thấp", description = "Danh sách sản phẩm có tồn khả dụng < mức tối thiểu (minQty)")
    public ApiResponse<List<StockLevelExpandedResponse>> getLowStock() {
        return ApiResponse.success("Lấy danh sách tồn kho thấp thành công", stockLevelService.findLowStock());
    }

    @GetMapping("/alerts/near-expiry")
    @Operation(summary = "Cảnh báo hàng sắp hết hạn", description = "Danh sách stock có expiryDate trong khoảng N ngày tới")
    public ApiResponse<List<NearExpiryStockResponse>> getNearExpiry(
            @Parameter(description = "Số ngày") @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID productId) {
        return ApiResponse.success("Lấy danh sách hàng sắp hết hạn thành công",
                stockLevelService.findNearExpiry(days, warehouseId, locationId, productId));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tồn kho", description = "Phân trang; lọc theo kho, vị trí hoặc sản phẩm")
    public ApiResponse<PagedResponse<?>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Mở rộng dữ liệu product,location,warehouse.")
            @RequestParam(required = false) String expand,
            @Parameter(description = "ID kho")
            @RequestParam(required = false) UUID warehouseId,
            @Parameter(description = "ID vị trí")
            @RequestParam(required = false) UUID locationId,
            @Parameter(description = "ID sản phẩm")
            @RequestParam(required = false) UUID productId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        String e = expand == null ? "" : expand.trim().toLowerCase();

        // Backward-compatible "light" mode when explicitly requested
        if (e.equals("ids") || e.equals("none")) {
            PagedResponse<StockLevelResponse> data = stockLevelService.findAll(pageable, warehouseId, locationId, productId);
            return ApiResponse.success("Lấy danh sách tồn kho thành công", data);
        }

        // Default: expand all
        boolean expandWarehouse = e.isBlank() || e.contains("warehouse");
        boolean expandLocation = e.isBlank() || e.contains("location");
        boolean expandProduct = e.isBlank() || e.contains("product");
        PagedResponse<StockLevelExpandedResponse> data = stockLevelService.findAllExpanded(
                pageable, warehouseId, locationId, productId, expandWarehouse, expandLocation, expandProduct);
        return ApiResponse.success("Lấy danh sách tồn kho thành công", data);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy tồn kho theo ID")
    public ApiResponse<StockLevelResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy tồn kho thành công", stockLevelService.findById(id));
    }

    @PostMapping("/adjust")
    @Operation(summary = "Điều chỉnh tồn kho", description = "qtyDelta > 0 nhập thêm; < 0 trừ (xuất). Dùng cho luồng putaway / xuất hàng.")
    public ApiResponse<StockLevelResponse> adjust(@Valid @RequestBody StockAdjustCommand command) {
        return ApiResponse.success("Điều chỉnh tồn kho thành công", stockLevelService.adjust(command));
    }

    @PostMapping("/adjust-reserved")
    @Operation(summary = "Điều chỉnh giữ chỗ", description = "reservedDelta > 0 giữ thêm; < 0 nhả chỗ (đơn xuất / hủy pick).")
    public ApiResponse<StockLevelResponse> adjustReserved(@Valid @RequestBody StockReserveCommand command) {
        return ApiResponse.success("Điều chỉnh giữ chỗ thành công", stockLevelService.adjustReserved(command));
    }

    @PostMapping
    @Operation(summary = "Tạo tồn kho")
    public ApiResponse<StockLevelResponse> create(@Valid @RequestBody CreateStockLevelRequest request) {
        return ApiResponse.success("Tạo tồn kho thành công", stockLevelService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật tồn kho")
    public ApiResponse<StockLevelResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateStockLevelRequest request) {
        return ApiResponse.success("Cập nhật tồn kho thành công", stockLevelService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tồn kho")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        stockLevelService.delete(id);
        return ApiResponse.success("Xóa tồn kho thành công", id.toString());
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Xuất báo cáo tồn kho ra Excel", description = "Xuất danh sách tồn kho theo kho / vị trí / sản phẩm")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID productId) {
        byte[] bytes = stockLevelExcelExportService.exportOnHandToXlsx(warehouseId, locationId, productId);
        String filename = "stock-report-" + java.time.LocalDate.now() + ".xlsx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

            @GetMapping(value = "/reports/near-expiry-export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            @Operation(summary = "Xuất danh sách hàng sắp hết hạn", description = "Lọc các stock level có expiryDate trong khoảng ngày sắp tới")
            public ResponseEntity<byte[]> exportNearExpiryXlsx(
                @RequestParam(defaultValue = "30") Integer days,
                @RequestParam(required = false) UUID warehouseId,
                @RequestParam(required = false) UUID locationId,
                @RequestParam(required = false) UUID productId) {
            byte[] bytes = stockLevelExcelExportService.exportNearExpiryToXlsx(warehouseId, locationId, productId, days);
            String filename = "near-expiry-stock-" + java.time.LocalDate.now() + ".xlsx";
            ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
            }
}
