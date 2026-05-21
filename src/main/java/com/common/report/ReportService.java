package com.common.report;

import com.common.report.dto.ReportSummaryResponse;
import com.common.report.dto.RevenueTrendResponse;
import com.common.report.dto.TopSkuResponse;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public ReportSummaryResponse getSummary() {
        return getSummary("30d", null);
    }

    public ReportSummaryResponse getSummary(String period, Integer year) {
        ReportPeriodRange range = resolvePeriod(period, year);
        BigDecimal totalRevenue = salesOrderRepository.sumTotalRevenueBetween(range.from(), range.to());
        long totalOrders = salesOrderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                range.from(), range.to());
        long activeOrders = salesOrderRepository.countByStatusNotInBetween(
                List.of(SalesOrderStatus.CANCELLED, SalesOrderStatus.DRAFT),
                range.from(),
                range.to());
        long shippedOrders = salesOrderRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                SalesOrderStatus.SHIPPED,
                range.from(),
                range.to());
        
        double completionRate = activeOrders == 0 ? 0 : (double) shippedOrders * 100 / activeOrders;
        
        List<RevenueTrendResponse> trend = getRevenueTrend(range);
        List<TopSkuResponse> topSkus = getTopSkus(5, range.from(), range.to());

        return new ReportSummaryResponse(
            totalRevenue,
            totalOrders,
            BigDecimal.valueOf(completionRate).setScale(2, RoundingMode.HALF_UP).doubleValue(),
            trend,
            topSkus
        );
    }

    public List<RevenueTrendResponse> getRevenueTrend(int days) {
        LocalDate today = LocalDate.now();
        LocalDate fromDay = today.minusDays(days - 1L);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime from = fromDay.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return getDailyRevenueTrend(fromDay, days, from, to);
    }

    private List<RevenueTrendResponse> getRevenueTrend(ReportPeriodRange range) {
        if (range.yearly()) {
            Map<Integer, BigDecimal> revenueByMonth = salesOrderItemRepository
                    .sumDailyRevenue(range.from(), range.to())
                    .stream()
                    .collect(Collectors.groupingBy(
                            view -> view.getOrderDate().getMonthValue(),
                            Collectors.mapping(
                                    SalesOrderItemRepository.DailyRevenueView::getRevenue,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

            List<RevenueTrendResponse> result = new ArrayList<>(12);
            for (Month month : Month.values()) {
                LocalDate date = LocalDate.of(range.from().getYear(), month, 1);
                result.add(new RevenueTrendResponse(date, revenueByMonth.getOrDefault(month.getValue(), BigDecimal.ZERO)));
            }
            return result;
        }

        long days = ChronoUnit.DAYS.between(range.fromDate(), range.toDate());
        int visibleDays = (int) Math.max(1, days);
        return getDailyRevenueTrend(range.fromDate(), visibleDays, range.from(), range.to());
    }

    private List<RevenueTrendResponse> getDailyRevenueTrend(
            LocalDate fromDay,
            int days,
            OffsetDateTime from,
            OffsetDateTime to) {
        Map<LocalDate, BigDecimal> revenueByDate = salesOrderItemRepository
                .sumDailyRevenue(from, to)
                .stream()
                .collect(Collectors.toMap(
                        SalesOrderItemRepository.DailyRevenueView::getOrderDate,
                        SalesOrderItemRepository.DailyRevenueView::getRevenue));

        List<RevenueTrendResponse> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = fromDay.plusDays(i);
            result.add(new RevenueTrendResponse(date, revenueByDate.getOrDefault(date, BigDecimal.ZERO)));
        }
        return result;
    }

    public List<TopSkuResponse> getTopSkus(int limit) {
        return salesOrderItemRepository.findTopSkus(limit).stream()
                .map(this::toTopSkuResponse)
                .toList();
    }

    private List<TopSkuResponse> getTopSkus(int limit, OffsetDateTime from, OffsetDateTime to) {
        return salesOrderItemRepository.findTopSkusBetween(from, to, limit).stream()
                .map(this::toTopSkuResponse)
                .toList();
    }

    private TopSkuResponse toTopSkuResponse(SalesOrderItemRepository.TopSkuView v) {
        return new TopSkuResponse(
                v.getProductId(),
                v.getProductSku(),
                v.getTotalQty(),
                v.getTotalRevenue()
        );
    }

    private ReportPeriodRange resolvePeriod(String rawPeriod, Integer selectedYear) {
        String period = rawPeriod == null || rawPeriod.isBlank() ? "30d" : rawPeriod;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        if ("year".equalsIgnoreCase(period)) {
            int year = selectedYear == null ? today.getYear() : selectedYear;
            LocalDate fromDate = LocalDate.of(year, 1, 1);
            LocalDate toDate = fromDate.plusYears(1);
            return new ReportPeriodRange(
                    fromDate.atStartOfDay(zone).toOffsetDateTime(),
                    toDate.atStartOfDay(zone).toOffsetDateTime(),
                    fromDate,
                    toDate,
                    true);
        }

        int days = switch (period) {
            case "today" -> 1;
            case "7d" -> 7;
            default -> 30;
        };
        LocalDate fromDate = today.minusDays(days - 1L);
        LocalDate toDate = today.plusDays(1);
        return new ReportPeriodRange(
                fromDate.atStartOfDay(zone).toOffsetDateTime(),
                toDate.atStartOfDay(zone).toOffsetDateTime(),
                fromDate,
                toDate,
                false);
    }

    private record ReportPeriodRange(
            OffsetDateTime from,
            OffsetDateTime to,
            LocalDate fromDate,
            LocalDate toDate,
            boolean yearly) {
    }
}
