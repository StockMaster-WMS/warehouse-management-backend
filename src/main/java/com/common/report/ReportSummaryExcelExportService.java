package com.common.report;

import com.common.report.dto.ReportSummaryResponse;
import com.common.report.dto.RevenueTrendResponse;
import com.common.report.dto.TopSkuResponse;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportSummaryExcelExportService {

    private final ReportService reportService;
    private final WarehouseRepository warehouseRepository;

    public byte[] exportToXlsx(String period, Integer year) {
        return exportToXlsx(period, year, null, null, null);
    }

    public byte[] exportToXlsx(String period, Integer year, LocalDate fromDate, LocalDate toDate,
            Collection<UUID> warehouseIds) {
        ReportSummaryResponse summary = reportService.getSummary(period, year, fromDate, toDate, warehouseIds);
        var detailRows = reportService.getShippedItemDetails(period, year, fromDate, toDate, warehouseIds);
        Map<UUID, Warehouse> warehouses = loadWarehouses(detailRows);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            writeMetadataSheet(workbook, summary, period, year, fromDate, toDate, warehouseIds, headerStyle);
            writeOverviewSheet(workbook, summary, period, year, headerStyle);
            writeRevenueSheet(workbook, summary, headerStyle);
            writeTopSkusSheet(workbook, summary, headerStyle);
            writeDetailSheet(workbook, detailRows, warehouses, headerStyle);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo file báo cáo", e);
        }
    }

    private void writeMetadataSheet(Workbook workbook, ReportSummaryResponse summary, String period, Integer year,
            LocalDate fromDate, LocalDate toDate, Collection<UUID> warehouseIds, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Thong tin loc");
        createHeader(sheet, headerStyle, "Tieu chi", "Gia tri");
        row(sheet, 1, "Ky bao cao", labelPeriod(period, year));
        row(sheet, 2, "Tu ngay", summary.fromDate());
        row(sheet, 3, "Den ngay", summary.toDate());
        row(sheet, 4, "Kho", summary.warehouseName() == null ? "Tat ca kho duoc phep" : summary.warehouseName());
        row(sheet, 5, "warehouseId", summary.warehouseId() == null ? "" : summary.warehouseId());
        row(sheet, 6, "Scope kho", warehouseIds == null ? "ALL" : warehouseIds.size() + " kho");
        row(sheet, 7, "Ngay xuat", LocalDate.now());
        setWidths(sheet, 28, 42);
    }

    private void writeOverviewSheet(Workbook workbook, ReportSummaryResponse summary, String period, Integer year,
            CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Tong quan");
        createHeader(sheet, headerStyle, "Chi so", "Gia tri");

        row(sheet, 1, "Ky bao cao", labelPeriod(period, year));
        row(sheet, 2, "Tu ngay", summary.fromDate());
        row(sheet, 3, "Den ngay", summary.toDate());
        row(sheet, 4, "Kho", summary.warehouseName() == null ? "Tat ca kho duoc phep" : summary.warehouseName());
        row(sheet, 5, "Tong doanh thu", summary.totalRevenue().doubleValue());
        row(sheet, 6, "Tong don hang", summary.totalOrders());
        row(sheet, 7, "Don hoat dong", summary.activeOrders());
        row(sheet, 8, "Don da xuat", summary.shippedOrders());
        row(sheet, 9, "Ty le hoan thanh (%)", summary.completionRate());

        setWidths(sheet, 28, 42);
    }

    private void writeRevenueSheet(Workbook workbook, ReportSummaryResponse summary, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Doanh thu");
        createHeader(sheet, headerStyle, "Ngay", "Doanh thu");

        int rowIndex = 1;
        for (RevenueTrendResponse item : summary.revenueTrend()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.date().toString());
            row.createCell(1).setCellValue(item.revenue().doubleValue());
        }

        setWidths(sheet, 18, 18);
    }

    private void writeTopSkusSheet(Workbook workbook, ReportSummaryResponse summary, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Top SKU");
        createHeader(sheet, headerStyle, "SKU", "So luong", "Doanh thu");

        int rowIndex = 1;
        for (TopSkuResponse item : summary.topSkus()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.productSku() == null ? "" : item.productSku());
            row.createCell(1).setCellValue(item.totalQty() == null ? 0 : item.totalQty());
            row.createCell(2).setCellValue(item.totalRevenue() == null ? 0 : item.totalRevenue().doubleValue());
        }

        setWidths(sheet, 24, 14, 18);
    }

    private void writeDetailSheet(Workbook workbook,
            Collection<SalesOrderItemRepository.ShippedItemReportView> detailRows,
            Map<UUID, Warehouse> warehouses,
            CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Chi tiet don xuat");
        createHeader(sheet, headerStyle,
                "Ngay tao don",
                "Ma don",
                "Khach hang",
                "Kho",
                "Ma kho",
                "SKU",
                "SL dat",
                "SL da xuat",
                "Don gia",
                "Doanh thu");

        int rowIndex = 1;
        for (SalesOrderItemRepository.ShippedItemReportView item : detailRows) {
            Warehouse warehouse = warehouses.get(item.getWarehouseId());
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
            row.createCell(1).setCellValue(nullToEmpty(item.getSoNumber()));
            row.createCell(2).setCellValue(nullToEmpty(item.getCustomerName()));
            row.createCell(3).setCellValue(warehouse == null ? "" : warehouse.getName());
            row.createCell(4).setCellValue(warehouse == null ? "" : warehouse.getCode());
            row.createCell(5).setCellValue(nullToEmpty(item.getProductSku()));
            row.createCell(6).setCellValue(item.getOrderedQty() == null ? 0 : item.getOrderedQty());
            row.createCell(7).setCellValue(item.getShippedQty() == null ? 0 : item.getShippedQty());
            row.createCell(8).setCellValue(item.getUnitPrice() == null ? 0 : item.getUnitPrice().doubleValue());
            row.createCell(9).setCellValue(item.getRevenue() == null ? 0 : item.getRevenue().doubleValue());
        }

        setWidths(sheet, 28, 18, 30, 30, 14, 22, 12, 14, 16, 18);
    }

    private void row(Sheet sheet, int index, String label, Object value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        if (value instanceof Number number) {
            row.createCell(1).setCellValue(number.doubleValue());
        } else {
            row.createCell(1).setCellValue(String.valueOf(value));
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void createHeader(Sheet sheet, CellStyle headerStyle, String... labels) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < labels.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void setWidths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private Map<UUID, Warehouse> loadWarehouses(Collection<SalesOrderItemRepository.ShippedItemReportView> detailRows) {
        List<UUID> ids = detailRows.stream()
                .map(SalesOrderItemRepository.ShippedItemReportView::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, warehouse -> warehouse));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String labelPeriod(String rawPeriod, Integer year) {
        String period = rawPeriod == null || rawPeriod.isBlank() ? "30d" : rawPeriod;
        return switch (period) {
            case "today" -> "Hom nay";
            case "7d" -> "7 ngay gan nhat";
            case "year" -> "Nam " + (year == null ? LocalDate.now().getYear() : year);
            default -> "30 ngay gan nhat";
        };
    }
}
