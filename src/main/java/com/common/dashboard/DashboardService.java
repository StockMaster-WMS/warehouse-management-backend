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
import com.outbound_service.repository.SalesOrderItemRepository;
import com.common.audit.AuditLogRepository;
import com.common.dashboard.dto.DashboardActivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DEFAULT_NEAR_EXPIRY_DAYS = 30;
    private static final int FLOW_DAYS = 7;

    private final StockLevelService stockLevelService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardSummaryResponse getSummary() {
        StockSummaryResponse stock = stockLevelService.getSummary(DEFAULT_NEAR_EXPIRY_DAYS);
        long openPurchaseOrders = purchaseOrderRepository.countByStatusNotIn(
                EnumSet.of(PurchaseOrderStatus.COMPLETED, PurchaseOrderStatus.CANCELLED));
        long openSalesOrders = salesOrderRepository.countByStatusNotIn(
                EnumSet.of(SalesOrderStatus.SHIPPED, SalesOrderStatus.CANCELLED));

        return new DashboardSummaryResponse(
                buildMetrics(stock, openPurchaseOrders, openSalesOrders),
                buildFlow(),
                buildNotices(stock, openPurchaseOrders, openSalesOrders),
                buildRecentActivities());
    }

    private List<DashboardMetricResponse> buildMetrics(
            StockSummaryResponse stock,
            long openPurchaseOrders,
            long openSalesOrders) {
        java.math.BigDecimal totalRevenue = salesOrderRepository.sumTotalRevenue();
        long customerCount = customerRepository.count();

        return List.of(
                new DashboardMetricResponse(
                        "revenue",
                        "Tổng doanh thu",
                        totalRevenue.longValue(),
                        "Từ đơn đã giao",
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

    private List<DashboardFlowPointResponse> buildFlow() {
        LocalDate today = LocalDate.now();
        LocalDate fromDay = today.minusDays(FLOW_DAYS - 1L);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime from = fromDay.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        Map<LocalDate, StockMovementRepository.DailyMovementView> movementByDate = stockMovementRepository
                .sumDailyMovements(from, to)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.DailyMovementView::getMovementDate,
                        Function.identity()));

        List<DashboardFlowPointResponse> result = new ArrayList<>(FLOW_DAYS);
        for (int i = 0; i < FLOW_DAYS; i++) {
            LocalDate date = fromDay.plusDays(i);
            StockMovementRepository.DailyMovementView movement = movementByDate.get(date);
            result.add(new DashboardFlowPointResponse(
                    date,
                    dayLabel(date.getDayOfWeek()),
                    movement == null || movement.getInboundQty() == null ? 0 : movement.getInboundQty(),
                    movement == null || movement.getOutboundQty() == null ? 0 : movement.getOutboundQty()));
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
}
