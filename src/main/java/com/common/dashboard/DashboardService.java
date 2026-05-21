package com.common.dashboard;

import com.common.dashboard.dto.DashboardFlowPointResponse;
import com.common.dashboard.dto.DashboardMetricResponse;
import com.common.dashboard.dto.DashboardNoticeResponse;
import com.common.dashboard.dto.DashboardSummaryResponse;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.SalesOrderRepository;
import com.warehouse_service.dto.response.StockSummaryResponse;
import com.warehouse_service.repository.StockMovementRepository;
import com.warehouse_service.service.StockLevelService;
import com.outbound_service.repository.CustomerRepository;
import com.common.audit.AuditLogRepository;
import com.common.dashboard.dto.DashboardActivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DEFAULT_NEAR_EXPIRY_DAYS = 30;
    private static final int DEFAULT_FLOW_DAYS = 7;
    private static final int MAX_FLOW_DAYS = 365;
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private final StockLevelService stockLevelService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardSummaryResponse getSummary(String period, Integer year) {
        DashboardRange range = resolveRange(period, year);
        StockSummaryResponse stock = stockLevelService.getSummary(DEFAULT_NEAR_EXPIRY_DAYS);
        long openPurchaseOrders = purchaseOrderRepository.countByStatusNotIn(
                EnumSet.of(PurchaseOrderStatus.COMPLETED, PurchaseOrderStatus.CANCELLED));
        long openSalesOrders = salesOrderRepository.countByStatusNotIn(
                EnumSet.of(SalesOrderStatus.SHIPPED, SalesOrderStatus.CANCELLED));

        return new DashboardSummaryResponse(
                buildMetrics(stock, openPurchaseOrders, openSalesOrders, range),
                buildFlow(range),
                buildNotices(stock, openPurchaseOrders, openSalesOrders),
                buildRecentActivities());
    }

    private DashboardRange resolveRange(String period, Integer requestedYear) {
        ZoneId zone = ZoneId.systemDefault();
        String normalized = period == null ? "7d" : period.trim().toLowerCase();
        LocalDate today = LocalDate.now(zone);

        if ("year".equals(normalized) || "365d".equals(normalized) || "1y".equals(normalized)) {
            int currentYear = today.getYear();
            int selectedYear = requestedYear == null ? currentYear : Math.max(2000, Math.min(requestedYear, currentYear + 1));
            LocalDate fromDay = LocalDate.of(selectedYear, 1, 1);
            LocalDate toDay = LocalDate.of(selectedYear + 1, 1, 1);
            return new DashboardRange(
                    fromDay.atStartOfDay(zone).toOffsetDateTime(),
                    toDay.atStartOfDay(zone).toOffsetDateTime(),
                    Math.max(1, Math.min(366, toDay.toEpochDay() - fromDay.toEpochDay())),
                    true,
                    selectedYear + "");
        }

        int days = resolvePeriodDays(normalized);
        LocalDate fromDay = today.minusDays(days - 1L);
        return new DashboardRange(
                fromDay.atStartOfDay(zone).toOffsetDateTime(),
                today.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
                days,
                false,
                days == 1 ? "Hôm nay" : "Trong " + days + " ngày");
    }

    private int resolvePeriodDays(String period) {
        if (period == null) {
            return DEFAULT_FLOW_DAYS;
        }
        return switch (period.trim().toLowerCase()) {
            case "today", "1d" -> 1;
            case "30d", "month", "1m" -> 30;
            case "7d", "week" -> DEFAULT_FLOW_DAYS;
            default -> DEFAULT_FLOW_DAYS;
        };
    }

    private List<DashboardMetricResponse> buildMetrics(
            StockSummaryResponse stock,
            long openPurchaseOrders,
            long openSalesOrders,
            DashboardRange range) {
        java.math.BigDecimal totalRevenue = salesOrderRepository.sumTotalRevenueBetween(range.from(), range.to());
        long customerCount = customerRepository.count();

        return List.of(
                new DashboardMetricResponse(
                        "revenue",
                        "Tổng doanh thu",
                        totalRevenue.longValue(),
                        range.metricLabel(),
                        "indigo"),
                new DashboardMetricResponse(
                        "customers",
                        "Khách hàng",
                        customerCount,
                        "Tổng số đối tác",
                        "emerald"),
                new DashboardMetricResponse(
                        "available-stock",
                        "Tồn khả dụng",
                        stock.totalQtyAvailable(),
                        stock.totalQtyReserved() > 0 ? stock.totalQtyReserved() + " đang giữ chỗ" : "Sẵn sàng xuất",
                        "amber"),
                new DashboardMetricResponse(
                        "low-stock",
                        "Tồn thấp",
                        stock.lowStockCount(),
                        stock.nearExpiryCount() > 0 ? stock.nearExpiryCount() + " lô sắp hết hạn" : "Trong ngưỡng an toàn",
                        stock.lowStockCount() > 0 ? "rose" : "emerald"));
    }

    private List<DashboardActivityResponse> buildRecentActivities() {
        return auditLogRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(log -> new DashboardActivityResponse(
                        log.getId(),
                        log.getModule(),
                        log.getActionType(),
                        log.getAction(),
                        log.getEntityName(),
                        log.getActorName(),
                        log.getCreatedAt()))
                .toList();
    }

    private List<DashboardFlowPointResponse> buildFlow(DashboardRange range) {
        Map<LocalDate, StockMovementRepository.DailyMovementView> movementByDate = stockMovementRepository
                .sumDailyMovements(range.from(), range.to())
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.DailyMovementView::getMovementDate,
                        Function.identity()));

        if (range.yearly()) {
            return buildMonthlyFlow(range, movementByDate);
        }

        int days = (int) Math.max(1, Math.min(range.days(), MAX_FLOW_DAYS));
        LocalDate fromDay = range.from().toLocalDate();
        List<DashboardFlowPointResponse> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = fromDay.plusDays(i);
            StockMovementRepository.DailyMovementView movement = movementByDate.get(date);
            result.add(new DashboardFlowPointResponse(
                    date,
                    days <= DEFAULT_FLOW_DAYS ? dayLabel(date.getDayOfWeek()) : SHORT_DATE_FORMATTER.format(date),
                    movement == null || movement.getInboundQty() == null ? 0 : movement.getInboundQty(),
                    movement == null || movement.getOutboundQty() == null ? 0 : movement.getOutboundQty()));
        }
        return result;
    }

    private List<DashboardFlowPointResponse> buildMonthlyFlow(
            DashboardRange range,
            Map<LocalDate, StockMovementRepository.DailyMovementView> movementByDate) {
        Map<YearMonth, long[]> movementByMonth = new LinkedHashMap<>();
        movementByDate.forEach((date, movement) -> {
            YearMonth month = YearMonth.from(date);
            long[] totals = movementByMonth.computeIfAbsent(month, ignored -> new long[2]);
            totals[0] += movement == null || movement.getInboundQty() == null ? 0 : movement.getInboundQty();
            totals[1] += movement == null || movement.getOutboundQty() == null ? 0 : movement.getOutboundQty();
        });

        int year = range.from().getYear();
        List<DashboardFlowPointResponse> result = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            long[] totals = movementByMonth.getOrDefault(yearMonth, new long[2]);
            result.add(new DashboardFlowPointResponse(
                    yearMonth.atDay(1),
                    "T" + month,
                    totals[0],
                    totals[1]));
        }
        return result;
    }

    private List<DashboardNoticeResponse> buildNotices(
            StockSummaryResponse stock,
            long openPurchaseOrders,
            long openSalesOrders) {
        List<DashboardNoticeResponse> notices = new ArrayList<>();

        if (stock.lowStockCount() > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Cảnh báo tồn kho thấp",
                    "Có " + stock.lowStockCount() + " mặt hàng dưới mức tồn tối thiểu.",
                    "error",
                    "Hiện tại"));
        }
        if (stock.nearExpiryCount() > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Hàng sắp hết hạn",
                    "Có " + stock.nearExpiryCount() + " lô cần xử lý trong "
                            + DEFAULT_NEAR_EXPIRY_DAYS + " ngày tới.",
                    "warning",
                    "Hiện tại"));
        }
        if (openSalesOrders > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Đơn xuất cần theo dõi",
                    "Có " + openSalesOrders + " đơn xuất chưa hoàn tất.",
                    "success",
                    "Hiện tại"));
        }
        if (openPurchaseOrders > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Đơn nhập đang xử lý",
                    "Có " + openPurchaseOrders + " đơn nhập chưa hoàn tất.",
                    "success",
                    "Hiện tại"));
        }
        if (notices.isEmpty()) {
            notices.add(new DashboardNoticeResponse(
                    "Vận hành ổn định",
                    "Không có cảnh báo tồn thấp hoặc hàng sắp hết hạn.",
                    "success",
                    "Hiện tại"));
        }

        return notices.stream().limit(4).toList();
    }

    private String dayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }

    private record DashboardRange(
            OffsetDateTime from,
            OffsetDateTime to,
            long days,
            boolean yearly,
            String metricLabel) {
    }
}
