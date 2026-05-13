package com.common.report;

import com.common.api.ApiResponse;
import com.common.report.dto.ReportSummaryResponse;
import com.common.report.dto.RevenueTrendResponse;
import com.common.report.dto.TopSkuResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "API thống kê báo cáo")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'REPORT_VIEWER')")
public class ReportController {

    private final ReportService reportService;
    private final InventoryExcelExportService inventoryExcelExportService;

    @GetMapping("/summary")
    @Operation(summary = "Tổng quan báo cáo", description = "Trả về doanh thu, tỷ lệ hoàn thành và các xu hướng chính")
    public ApiResponse<ReportSummaryResponse> getSummary() {
        return ApiResponse.success("Lấy tổng quan báo cáo thành công", reportService.getSummary());
    }

    @GetMapping("/revenue-trend")
    @Operation(summary = "Xu hướng doanh thu", description = "Trả về doanh thu theo ngày")
    public ApiResponse<List<RevenueTrendResponse>> getRevenueTrend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success("Lấy xu hướng doanh thu thành công", reportService.getRevenueTrend(days));
    }

    @GetMapping("/top-skus")
    @Operation(summary = "Top sản phẩm", description = "Trả về danh sách sản phẩm bán chạy nhất")
    public ApiResponse<List<TopSkuResponse>> getTopSkus(@RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success("Lấy danh sách sản phẩm hàng đầu thành công", reportService.getTopSkus(limit));
    }

    @GetMapping(value = "/inventory/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Xuất báo cáo tồn kho ra Excel")
    public ResponseEntity<byte[]> exportInventory() {
        byte[] bytes = inventoryExcelExportService.exportToXlsx();
        String filename = "inventory-report-" + java.time.LocalDate.now() + ".xlsx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }
}
