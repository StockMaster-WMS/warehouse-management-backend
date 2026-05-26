package com.common.report;

import com.common.report.dto.ReportSummaryResponse;
import com.common.report.dto.RevenueTrendResponse;
import com.common.report.dto.TopSkuResponse;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.text.NumberFormat;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportSummaryExcelExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle moneyStyle = createMoneyStyle(workbook);
            writeMetadataSheet(workbook, summary, period, year, fromDate, toDate, warehouseIds, detailRows.size(), headerStyle);
            writeOverviewSheet(workbook, summary, period, year, detailRows.size(), headerStyle, numberStyle, moneyStyle);
            writeInsightsSheet(workbook, summary, detailRows, headerStyle);
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
            LocalDate fromDate, LocalDate toDate, Collection<UUID> warehouseIds, int detailCount, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Thông tin lọc");
        createHeader(sheet, headerStyle, "Tiêu chí", "Giá trị");
        row(sheet, 1, "Kỳ báo cáo", labelPeriod(period, year));
        row(sheet, 2, "Từ ngày", formatDate(summary.fromDate()));
        row(sheet, 3, "Đến ngày", formatDate(summary.toDate()));
        row(sheet, 4, "Kho", summary.warehouseName() == null ? "Tất cả kho được phép" : summary.warehouseName());
        row(sheet, 5, "Mã định danh kho", summary.warehouseId() == null ? "" : summary.warehouseId());
        row(sheet, 6, "Phạm vi dữ liệu", warehouseIds == null ? "Toàn hệ thống" : warehouseIds.size() + " kho được phân quyền");
        row(sheet, 7, "Bộ lọc ngày tùy chỉnh", fromDate == null && toDate == null ? "Không" : "Có");
        row(sheet, 8, "Số dòng chi tiết đơn xuất", detailCount);
        row(sheet, 9, "Ngày xuất báo cáo", formatDate(LocalDate.now()));
        setWidths(sheet, 28, 42);
    }

    private void writeOverviewSheet(Workbook workbook, ReportSummaryResponse summary, String period, Integer year,
            int detailCount, CellStyle headerStyle, CellStyle numberStyle, CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Tổng quan");
        createHeader(sheet, headerStyle, "Chỉ số", "Giá trị", "Ghi chú");

        long unshippedOrders = Math.max(0, summary.activeOrders() - summary.shippedOrders());
        BigDecimal averageRevenuePerShippedOrder = summary.shippedOrders() == 0
                ? BigDecimal.ZERO
                : summary.totalRevenue().divide(BigDecimal.valueOf(summary.shippedOrders()), 2, RoundingMode.HALF_UP);
        RevenueTrendResponse bestDay = bestRevenueDay(summary);
        TopSkuResponse topSku = topRevenueSku(summary);

        row(sheet, 1, "Kỳ báo cáo", labelPeriod(period, year), "Khoảng thời gian dùng cho toàn bộ báo cáo");
        row(sheet, 2, "Kho", summary.warehouseName() == null ? "Tất cả kho được phép" : summary.warehouseName(), "Theo quyền truy cập hiện tại");
        row(sheet, 3, "Tổng doanh thu", summary.totalRevenue().doubleValue(), "Chỉ tính đơn đã xuất", moneyStyle);
        row(sheet, 4, "Tổng đơn tạo mới", summary.totalOrders(), "Tất cả đơn phát sinh trong kỳ", numberStyle);
        row(sheet, 5, "Đơn đang hoạt động", summary.activeOrders(), "Loại trừ đơn nháp và đã hủy", numberStyle);
        row(sheet, 6, "Đơn đã xuất", summary.shippedOrders(), "Đơn có trạng thái SHIPPED", numberStyle);
        row(sheet, 7, "Đơn chưa hoàn tất", unshippedOrders, "Đơn hoạt động nhưng chưa xuất xong", numberStyle);
        row(sheet, 8, "Tỷ lệ hoàn thành", summary.completionRate() + "%", "Đơn đã xuất / đơn đang hoạt động");
        row(sheet, 9, "Doanh thu bình quân / đơn đã xuất", averageRevenuePerShippedOrder.doubleValue(), "Tổng doanh thu chia cho số đơn đã xuất", moneyStyle);
        row(sheet, 10, "Ngày doanh thu cao nhất", bestDay == null ? "Chưa có dữ liệu" : formatDate(bestDay.date()), bestDay == null ? "" : formatMoney(bestDay.revenue()));
        row(sheet, 11, "SKU đóng góp cao nhất", topSku == null ? "Chưa có dữ liệu" : topSku.productSku(), topSku == null ? "" : formatMoney(topSku.totalRevenue()));
        row(sheet, 12, "Số dòng chi tiết", detailCount, "Số dòng hàng xuất trong sheet chi tiết", numberStyle);

        setWidths(sheet, 34, 28, 46);
    }

    private void writeInsightsSheet(Workbook workbook, ReportSummaryResponse summary,
            Collection<SalesOrderItemRepository.ShippedItemReportView> detailRows,
            CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Nhận định");
        createHeader(sheet, headerStyle, "Nội dung", "Phân tích");

        long unshippedOrders = Math.max(0, summary.activeOrders() - summary.shippedOrders());
        RevenueTrendResponse bestDay = bestRevenueDay(summary);
        TopSkuResponse topSku = topRevenueSku(summary);
        long revenueDays = summary.revenueTrend().stream()
                .filter(item -> item.revenue() != null && item.revenue().compareTo(BigDecimal.ZERO) > 0)
                .count();

        row(sheet, 1, "Tình hình doanh thu", summary.totalRevenue().compareTo(BigDecimal.ZERO) == 0
                ? "Chưa ghi nhận doanh thu từ đơn đã xuất trong kỳ."
                : "Doanh thu đã ghi nhận " + formatMoney(summary.totalRevenue()) + " từ " + summary.shippedOrders() + " đơn đã xuất.");
        row(sheet, 2, "Hiệu suất hoàn tất đơn", "Tỷ lệ hoàn thành đạt " + summary.completionRate()
                + "%; còn " + unshippedOrders + " đơn hoạt động chưa hoàn tất.");
        row(sheet, 3, "Nhịp doanh thu", bestDay == null
                ? "Chưa có ngày phát sinh doanh thu."
                : "Ngày cao nhất là " + formatDate(bestDay.date()) + " với " + formatMoney(bestDay.revenue())
                        + "; có " + revenueDays + " ngày/tháng phát sinh doanh thu.");
        row(sheet, 4, "SKU trọng điểm", topSku == null
                ? "Chưa có SKU bán ra trong kỳ."
                : topSku.productSku() + " đang đóng góp cao nhất với " + formatMoney(topSku.totalRevenue())
                        + " và " + formatNumber(topSku.totalQty()) + " đơn vị đã xuất.");
        row(sheet, 5, "Độ chi tiết dữ liệu", "File có " + detailRows.size()
                + " dòng hàng xuất để đối soát theo đơn, kho, SKU, số lượng, đơn giá và doanh thu.");
        row(sheet, 6, "Khuyến nghị vận hành", buildRecommendation(summary, unshippedOrders));

        setWidths(sheet, 28, 110);
    }

    private void writeRevenueSheet(Workbook workbook, ReportSummaryResponse summary, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Doanh thu");
        createHeader(sheet, headerStyle, "Ngày", "Doanh thu", "Tỷ trọng", "Ghi chú");

        int rowIndex = 1;
        BigDecimal totalRevenue = summary.totalRevenue() == null ? BigDecimal.ZERO : summary.totalRevenue();
        for (RevenueTrendResponse item : summary.revenueTrend()) {
            Row row = sheet.createRow(rowIndex++);
            BigDecimal revenue = item.revenue() == null ? BigDecimal.ZERO : item.revenue();
            row.createCell(0).setCellValue(formatDate(item.date()));
            row.createCell(1).setCellValue(revenue.doubleValue());
            row.createCell(2).setCellValue(totalRevenue.compareTo(BigDecimal.ZERO) == 0
                    ? "0%"
                    : revenue.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP) + "%");
            row.createCell(3).setCellValue(revenue.compareTo(BigDecimal.ZERO) > 0 ? "Có phát sinh doanh thu" : "Không phát sinh");
        }

        setWidths(sheet, 18, 20, 14, 28);
    }

    private void writeTopSkusSheet(Workbook workbook, ReportSummaryResponse summary, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Top SKU");
        createHeader(sheet, headerStyle, "Hạng", "Mã hàng", "Tên sản phẩm", "Số lượng đã xuất", "Doanh thu", "Tỷ trọng doanh thu");

        int rowIndex = 1;
        BigDecimal totalRevenue = summary.totalRevenue() == null ? BigDecimal.ZERO : summary.totalRevenue();
        for (TopSkuResponse item : summary.topSkus()) {
            Row row = sheet.createRow(rowIndex++);
            BigDecimal revenue = item.totalRevenue() == null ? BigDecimal.ZERO : item.totalRevenue();
            row.createCell(0).setCellValue(rowIndex - 1);
            row.createCell(1).setCellValue(item.productSku() == null ? "" : item.productSku());
            row.createCell(2).setCellValue(item.productName() == null ? "" : item.productName());
            row.createCell(3).setCellValue(item.totalQty() == null ? 0 : item.totalQty());
            row.createCell(4).setCellValue(revenue.doubleValue());
            row.createCell(5).setCellValue(totalRevenue.compareTo(BigDecimal.ZERO) == 0
                    ? "0%"
                    : revenue.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP) + "%");
        }

        setWidths(sheet, 10, 24, 36, 20, 18, 20);
    }

    private void writeDetailSheet(Workbook workbook,
            Collection<SalesOrderItemRepository.ShippedItemReportView> detailRows,
            Map<UUID, Warehouse> warehouses,
            CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Chi tiết đơn xuất");
        createHeader(sheet, headerStyle,
                "Ngày tạo đơn",
                "Mã đơn",
                "Khách hàng",
                "Kho",
                "Mã kho",
                "SKU",
                "SL đặt",
                "SL đã xuất",
                "Đơn giá",
                "Doanh thu");

        int rowIndex = 1;
        for (SalesOrderItemRepository.ShippedItemReportView item : detailRows) {
            Warehouse warehouse = warehouses.get(item.getWarehouseId());
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(formatDateTime(item.getCreatedAt()));
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

    private void row(Sheet sheet, int index, String label, Object value, String note) {
        row(sheet, index, label, value);
        sheet.getRow(index).createCell(2).setCellValue(note);
    }

    private void row(Sheet sheet, int index, String label, Number value, String note, CellStyle valueStyle) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        var valueCell = row.createCell(1);
        valueCell.setCellValue(value.doubleValue());
        valueCell.setCellStyle(valueStyle);
        row.createCell(2).setCellValue(note);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private CellStyle createMoneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
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
            case "today" -> "Hôm nay";
            case "7d" -> "7 ngày gần nhất";
            case "year" -> "Năm " + (year == null ? LocalDate.now().getYear() : year);
            default -> "30 ngày gần nhất";
        };
    }

    private RevenueTrendResponse bestRevenueDay(ReportSummaryResponse summary) {
        return summary.revenueTrend().stream()
                .filter(item -> item.revenue() != null)
                .max((left, right) -> left.revenue().compareTo(right.revenue()))
                .filter(item -> item.revenue().compareTo(BigDecimal.ZERO) > 0)
                .orElse(null);
    }

    private TopSkuResponse topRevenueSku(ReportSummaryResponse summary) {
        return summary.topSkus().stream()
                .filter(item -> item.totalRevenue() != null)
                .max((left, right) -> left.totalRevenue().compareTo(right.totalRevenue()))
                .filter(item -> item.totalRevenue().compareTo(BigDecimal.ZERO) > 0)
                .orElse(null);
    }

    private String buildRecommendation(ReportSummaryResponse summary, long unshippedOrders) {
        if (summary.totalRevenue().compareTo(BigDecimal.ZERO) == 0) {
            return "Kiểm tra lại luồng xuất hàng và trạng thái SHIPPED để đảm bảo doanh thu được ghi nhận đúng kỳ.";
        }
        if (unshippedOrders > 0) {
            return "Ưu tiên xử lý " + unshippedOrders + " đơn đang hoạt động chưa hoàn tất để cải thiện tỷ lệ hoàn thành.";
        }
        if (summary.topSkus().isEmpty()) {
            return "Chưa đủ dữ liệu SKU để đánh giá sản phẩm trọng điểm.";
        }
        return "Theo dõi tồn kho và kế hoạch bổ sung cho các SKU doanh thu cao để tránh gián đoạn xuất hàng.";
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : DATE_FORMATTER.format(value);
    }

    private String formatDateTime(Instant value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value.atZone(ZoneId.systemDefault()));
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return formatNumber(safeValue) + " ₫";
    }

    private String formatNumber(Number value) {
        if (value == null) {
            return "0";
        }
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value);
    }
}
