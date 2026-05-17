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
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        BigDecimal totalRevenue = salesOrderRepository.sumTotalRevenue(thirtyDaysAgo);
        long totalOrders = salesOrderRepository.count();
        long activeOrders = salesOrderRepository.countByStatusNotIn(List.of(SalesOrderStatus.CANCELLED, SalesOrderStatus.DRAFT));
        long shippedOrders = salesOrderRepository.countByStatus(SalesOrderStatus.SHIPPED);
        
        double completionRate = activeOrders == 0 ? 0 : (double) shippedOrders * 100 / activeOrders;
        
        List<RevenueTrendResponse> trend = getRevenueTrend(7);
        List<TopSkuResponse> topSkus = getTopSkus(5);

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
                .map(v -> new TopSkuResponse(
                        v.getProductId(),
                        v.getProductSku(),
                        v.getTotalQty(),
                        v.getTotalRevenue()
                ))
                .toList();
    }
}
