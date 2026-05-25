package com.common.dashboard;

import com.common.dashboard.dto.DashboardFlowPointResponse;
import com.common.dashboard.dto.DashboardMetricResponse;
import com.common.dashboard.dto.DashboardNoticeResponse;
import com.common.dashboard.dto.DashboardOperationsResponse;
import com.common.dashboard.dto.DashboardSummaryResponse;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.warehouse_service.dto.response.StockSummaryResponse;
import com.warehouse_service.repository.CycleCountRepository;
import com.warehouse_service.repository.StockMovementRepository;
import com.warehouse_service.service.StockLevelService;
import com.outbound_service.repository.CustomerRepository;
import com.common.audit.AuditLogRepository;
import com.common.dashboard.dto.DashboardActivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DEFAULT_NEAR_EXPIRY_DAYS = 30;
    private static final int DEFAULT_FLOW_DAYS = 7;
    private static final int MAX_FLOW_DAYS = 365;
    private static final int OVERDUE_PICKING_HOURS = 24;
    private static final int LARGE_VARIANCE_THRESHOLD = 10;
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private final StockLevelService stockLevelService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PickingItemRepository pickingItemRepository;
    private final CycleCountRepository cycleCountRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardSummaryResponse getSummary(String period, Integer year) {
        return getSummary(period, year, null);
    }

    public DashboardSummaryResponse getSummary(String period, Integer year, Set<UUID> visibleWarehouseIds) {
        DashboardRange range = resolveRange(period, year);
        StockSummaryResponse stock = stockLevelService.getSummary(DEFAULT_NEAR_EXPIRY_DAYS, visibleWarehouseIds);
        boolean scoped = visibleWarehouseIds != null;
        boolean emptyScope = scoped && visibleWarehouseIds.isEmpty();
        long openPurchaseOrders = emptyScope ? 0 : scoped
                ? purchaseOrderRepository.countByStatusNotInAndWarehouseIdIn(
                        EnumSet.of(PurchaseOrderStatus.COMPLETED, PurchaseOrderStatus.CANCELLED), visibleWarehouseIds)
                : purchaseOrderRepository.countByStatusNotIn(
                        EnumSet.of(PurchaseOrderStatus.COMPLETED, PurchaseOrderStatus.CANCELLED));
        long openSalesOrders = emptyScope ? 0 : scoped
                ? salesOrderRepository.countByStatusNotInAndWarehouseIdIn(
                        EnumSet.of(SalesOrderStatus.SHIPPED, SalesOrderStatus.CANCELLED), visibleWarehouseIds)
                : salesOrderRepository.countByStatusNotIn(
                        EnumSet.of(SalesOrderStatus.SHIPPED, SalesOrderStatus.CANCELLED));
        DashboardOperationsResponse operations = buildOperations(stock, visibleWarehouseIds);

        return new DashboardSummaryResponse(
                buildMetrics(stock, openPurchaseOrders, openSalesOrders, range, visibleWarehouseIds),
                operations,
                buildFlow(range, visibleWarehouseIds),
                buildNotices(stock, openPurchaseOrders, openSalesOrders, operations),
                buildRecentActivities(visibleWarehouseIds));
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
            DashboardRange range,
            Set<UUID> visibleWarehouseIds) {
        boolean scoped = visibleWarehouseIds != null;
        java.math.BigDecimal totalRevenue = scoped
                ? visibleWarehouseIds.isEmpty() ? java.math.BigDecimal.ZERO
                        : salesOrderRepository.sumTotalRevenueBetweenInWarehouses(range.from(), range.to(), visibleWarehouseIds)
                : salesOrderRepository.sumTotalRevenueBetween(range.from(), range.to());
        long customerCount = scoped
                ? visibleWarehouseIds.isEmpty() ? 0 : salesOrderRepository.countDistinctCustomersInWarehouses(visibleWarehouseIds)
                : customerRepository.count();

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

    private DashboardOperationsResponse buildOperations(StockSummaryResponse stock, Set<UUID> visibleWarehouseIds) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime overdueCutoff = now.minusHours(OVERDUE_PICKING_HOURS);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime todayStart = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime tomorrowStart = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        boolean scoped = visibleWarehouseIds != null;
        boolean emptyScope = scoped && visibleWarehouseIds.isEmpty();

        long pendingPickingOrders = emptyScope ? 0 : scoped
                ? salesOrderRepository.countByStatusInWarehouses(SalesOrderStatus.PICKING, visibleWarehouseIds)
                : salesOrderRepository.countByStatus(SalesOrderStatus.PICKING);
        long overduePickingTasks = emptyScope ? 0 : scoped
                ? pickingItemRepository.countOverduePickingTasksInWarehouses(overdueCutoff, visibleWarehouseIds)
                : pickingItemRepository.countOverduePickingTasks(overdueCutoff);
        long outboundOrdersWithoutPicking = emptyScope ? 0 : scoped
                ? salesOrderRepository.countWithoutPickingByStatusInWarehouses(SalesOrderStatus.PENDING, visibleWarehouseIds)
                : salesOrderRepository.countWithoutPickingByStatus(SalesOrderStatus.PENDING);

        long countedItems = emptyScope ? 0 : scoped
                ? cycleCountRepository.countApprovedCountedItemsInWarehouses(visibleWarehouseIds)
                : cycleCountRepository.countApprovedCountedItems();
        long accurateItems = emptyScope ? 0 : scoped
                ? cycleCountRepository.countAccurateApprovedCountedItemsInWarehouses(visibleWarehouseIds)
                : cycleCountRepository.countAccurateApprovedCountedItems();
        double accuracy = countedItems == 0 ? 100.0 : Math.round((accurateItems * 10000.0 / countedItems)) / 100.0;
        long largeVarianceItems = emptyScope ? 0 : scoped
                ? cycleCountRepository.countLargeVarianceItemsInWarehouses(LARGE_VARIANCE_THRESHOLD, visibleWarehouseIds)
                : cycleCountRepository.countLargeVarianceItems(LARGE_VARIANCE_THRESHOLD);

        long completedToday = emptyScope ? 0 : scoped
                ? salesOrderRepository.countByStatusBetweenInWarehouses(
                        SalesOrderStatus.SHIPPED, todayStart, tomorrowStart, visibleWarehouseIds)
                : salesOrderRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        SalesOrderStatus.SHIPPED, todayStart, tomorrowStart);

        return new DashboardOperationsResponse(
                pendingPickingOrders,
                overduePickingTasks,
                stock.lowStockCount(),
                stock.nearExpiryCount(),
                accuracy,
                completedToday,
                outboundOrdersWithoutPicking,
                largeVarianceItems);
    }

    private List<DashboardActivityResponse> buildRecentActivities(Set<UUID> visibleWarehouseIds) {
        boolean scoped = visibleWarehouseIds != null;
        boolean emptyScope = scoped && visibleWarehouseIds.isEmpty();
        List<com.common.audit.AuditLog> logs = emptyScope ? List.of() : scoped
                ? auditLogRepository.findAll((root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(root.get("metadata").isNotNull());
                    predicates.add(cb.or(visibleWarehouseIds.stream()
                            .map(id -> cb.like(root.get("metadata"), "%" + id + "%"))
                            .toArray(Predicate[]::new)));
                    return cb.and(predicates.toArray(Predicate[]::new));
                }, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent()
                : auditLogRepository.findTop5ByOrderByCreatedAtDesc();

        return logs.stream()
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

    private List<DashboardFlowPointResponse> buildFlow(DashboardRange range, Set<UUID> visibleWarehouseIds) {
        boolean scoped = visibleWarehouseIds != null;
        List<StockMovementRepository.DailyMovementView> movementRows = scoped
                ? visibleWarehouseIds.isEmpty() ? List.of()
                        : stockMovementRepository.sumDailyMovementsInWarehouses(range.from(), range.to(), visibleWarehouseIds)
                : stockMovementRepository.sumDailyMovements(range.from(), range.to());
        Map<LocalDate, StockMovementRepository.DailyMovementView> movementByDate = movementRows
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
            long openSalesOrders,
            DashboardOperationsResponse operations) {
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
        if (operations.outboundOrdersWithoutPicking() > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Đơn xuất chưa có picking",
                    "Có " + operations.outboundOrdersWithoutPicking() + " đơn xuất đang chờ tạo nhiệm vụ lấy hàng.",
                    "warning",
                    "Hiện tại"));
        }
        if (operations.overduePickingTasks() > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Task picking quá hạn",
                    "Có " + operations.overduePickingTasks() + " nhiệm vụ lấy hàng quá "
                            + OVERDUE_PICKING_HOURS + " giờ chưa hoàn tất.",
                    "error",
                    "Hiện tại"));
        }
        if (operations.largeVarianceCycleCountItems() > 0) {
            notices.add(new DashboardNoticeResponse(
                    "Kiểm kê lệch lớn",
                    "Có " + operations.largeVarianceCycleCountItems()
                            + " dòng kiểm kê lệch từ " + LARGE_VARIANCE_THRESHOLD + " đơn vị trở lên.",
                    "warning",
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

        return notices.stream().limit(6).toList();
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
