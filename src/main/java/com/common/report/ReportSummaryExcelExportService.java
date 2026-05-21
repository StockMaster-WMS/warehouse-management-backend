package com.common.report;

import com.common.report.dto.ReportSummaryResponse;
import com.common.report.dto.RevenueTrendResponse;
import com.common.report.dto.TopSkuResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReportSummaryExcelExportService {

    private final ReportService reportService;

    public byte[] exportToXlsx(String period, Integer year) {
        ReportSummaryResponse summary = reportService.getSummary(period, year);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeOverviewSheet(workbook, summary, period, year);
            writeRevenueSheet(workbook, summary);
            writeTopSkusSheet(workbook, summary);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo file báo cáo", e);
        }
    }

    private void writeOverviewSheet(Workbook workbook, ReportSummaryResponse summary, String period, Integer year) {
        Sheet sheet = workbook.createSheet("Tong quan");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Chi so");
        header.createCell(1).setCellValue("Gia tri");

        row(sheet, 1, "Ky bao cao", labelPeriod(period, year));
        row(sheet, 2, "Ngay xuat", LocalDate.now().toString());
        row(sheet, 3, "Tong doanh thu", summary.totalRevenue().doubleValue());
        row(sheet, 4, "Tong don hang", summary.totalOrders());
        row(sheet, 5, "Ty le hoan thanh (%)", summary.completionRate());

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeRevenueSheet(Workbook workbook, ReportSummaryResponse summary) {
        Sheet sheet = workbook.createSheet("Doanh thu");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Ngay");
        header.createCell(1).setCellValue("Doanh thu");

        int rowIndex = 1;
        for (RevenueTrendResponse item : summary.revenueTrend()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.date().toString());
            row.createCell(1).setCellValue(item.revenue().doubleValue());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeTopSkusSheet(Workbook workbook, ReportSummaryResponse summary) {
        Sheet sheet = workbook.createSheet("Top SKU");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("SKU");
        header.createCell(1).setCellValue("So luong");
        header.createCell(2).setCellValue("Doanh thu");

        int rowIndex = 1;
        for (TopSkuResponse item : summary.topSkus()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.productSku() == null ? "" : item.productSku());
            row.createCell(1).setCellValue(item.totalQty() == null ? 0 : item.totalQty());
            row.createCell(2).setCellValue(item.totalRevenue() == null ? 0 : item.totalRevenue().doubleValue());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
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
