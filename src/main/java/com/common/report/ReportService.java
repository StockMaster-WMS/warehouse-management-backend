package com.common.report;

import com.common.report.dto.ReportSummaryResponse;
import com.common.report.dto.RevenueTrendResponse;
import com.common.report.dto.TopSkuResponse;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.product_service.entity.Product;
import com.product_service.repository.ProductRepository;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.WarehouseRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final List<SalesOrderStatus> FULFILLED_SALES_STATUSES =
            List.of(SalesOrderStatus.SHIPPED, SalesOrderStatus.COMPLETED);

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;

    public ReportSummaryResponse getSummary() {
        return getSummary("30d", null, null, null, null);
    }

    public ReportSummaryResponse getSummary(String period, Integer year) {
        return getSummary(period, year, null, null, null);
    }

    public ReportSummaryResponse getSummary(String period, Integer year, LocalDate fromDate, LocalDate toDate,
            Collection<UUID> warehouseIds) {
        ReportPeriodRange range = resolvePeriod(period, year, fromDate, toDate);
        boolean scoped = warehouseIds != null;
        List<UUID> scopedWarehouseIds = scoped ? warehouseIds.stream().distinct().toList() : null;

        BigDecimal totalRevenue = scoped
                ? scopedWarehouseIds.isEmpty() ? BigDecimal.ZERO
                        : salesOrderRepository.sumTotalRevenueBetweenInWarehouses(range.from(), range.to(),
                                scopedWarehouseIds)
                : salesOrderRepository.sumTotalRevenueBetween(range.from(), range.to());
        long totalOrders = scoped
                ? scopedWarehouseIds.isEmpty() ? 0
                        : salesOrderRepository.countCreatedBetweenInWarehouses(range.from(), range.to(),
                                scopedWarehouseIds)
                : salesOrderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        range.from(), range.to());
        long activeOrders = scoped
                ? scopedWarehouseIds.isEmpty() ? 0
                        : salesOrderRepository.countByStatusNotInBetweenInWarehouses(
                                List.of(SalesOrderStatus.CANCELLED, SalesOrderStatus.DRAFT),
                                range.from(),
                                range.to(),
                                scopedWarehouseIds)
                : salesOrderRepository.countByStatusNotInBetween(
                        List.of(SalesOrderStatus.CANCELLED, SalesOrderStatus.DRAFT),
                        range.from(),
                        range.to());
        long shippedOrders = scoped
                ? scopedWarehouseIds.isEmpty() ? 0
                        : salesOrderRepository.countByStatusInBetweenInWarehouses(
                                FULFILLED_SALES_STATUSES,
                                range.from(),
                                range.to(),
                                scopedWarehouseIds)
                : salesOrderRepository.countByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        FULFILLED_SALES_STATUSES,
                        range.from(),
                        range.to());
        
        double completionRate = activeOrders == 0 ? 0 : (double) shippedOrders * 100 / activeOrders;
        
        List<RevenueTrendResponse> trend = getRevenueTrend(range, scopedWarehouseIds);
        List<TopSkuResponse> topSkus = getTopSkus(10, range.from(), range.to(), scopedWarehouseIds);
        Warehouse selectedWarehouse = scopedWarehouseIds != null && scopedWarehouseIds.size() == 1
                ? warehouseRepository.findById(scopedWarehouseIds.get(0)).orElse(null)
                : null;

        return new ReportSummaryResponse(
            totalRevenue,
            totalOrders,
            activeOrders,
            shippedOrders,
            BigDecimal.valueOf(completionRate).setScale(2, RoundingMode.HALF_UP).doubleValue(),
            range.fromDate(),
            range.toDate().minusDays(1),
            selectedWarehouse == null ? null : selectedWarehouse.getId(),
            selectedWarehouse == null ? null : selectedWarehouse.getName(),
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

    private List<RevenueTrendResponse> getRevenueTrend(ReportPeriodRange range, Collection<UUID> warehouseIds) {
        if (range.yearly()) {
            List<SalesOrderItemRepository.DailyRevenueView> revenueRows = warehouseIds == null
                    ? salesOrderItemRepository.sumDailyRevenue(range.from(), range.to())
                    : warehouseIds.isEmpty()
                            ? List.of()
                            : salesOrderItemRepository.sumDailyRevenueInWarehouses(range.from(), range.to(),
                                    warehouseIds);
            Map<Integer, BigDecimal> revenueByMonth = revenueRows
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
        return getDailyRevenueTrend(range.fromDate(), visibleDays, range.from(), range.to(), warehouseIds);
    }

    private List<RevenueTrendResponse> getDailyRevenueTrend(
            LocalDate fromDay,
            int days,
            OffsetDateTime from,
            OffsetDateTime to) {
        return getDailyRevenueTrend(fromDay, days, from, to, null);
    }

    private List<RevenueTrendResponse> getDailyRevenueTrend(
            LocalDate fromDay,
            int days,
            OffsetDateTime from,
            OffsetDateTime to,
            Collection<UUID> warehouseIds) {
        List<SalesOrderItemRepository.DailyRevenueView> rows = warehouseIds == null
                ? salesOrderItemRepository.sumDailyRevenue(from, to)
                : warehouseIds.isEmpty()
                        ? List.of()
                        : salesOrderItemRepository.sumDailyRevenueInWarehouses(from, to, warehouseIds);
        Map<LocalDate, BigDecimal> revenueByDate = rows
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
        return toTopSkuResponses(salesOrderItemRepository.findTopSkus(limit));
    }

    private List<TopSkuResponse> getTopSkus(int limit, OffsetDateTime from, OffsetDateTime to) {
        return toTopSkuResponses(salesOrderItemRepository.findTopSkusBetween(from, to, limit));
    }

    public List<SalesOrderItemRepository.ShippedItemReportView> getShippedItemDetails(
            String period,
            Integer year,
            LocalDate fromDate,
            LocalDate toDate,
            Collection<UUID> warehouseIds) {
        ReportPeriodRange range = resolvePeriod(period, year, fromDate, toDate);
        if (warehouseIds == null) {
            return salesOrderItemRepository.findShippedItemDetailsBetween(range.from(), range.to());
        }
        List<UUID> scopedWarehouseIds = warehouseIds.stream().distinct().toList();
        if (scopedWarehouseIds.isEmpty()) {
            return List.of();
        }
        return salesOrderItemRepository.findShippedItemDetailsBetweenInWarehouses(
                range.from(), range.to(), scopedWarehouseIds);
    }

    private List<TopSkuResponse> getTopSkus(int limit, OffsetDateTime from, OffsetDateTime to,
            Collection<UUID> warehouseIds) {
        if (warehouseIds == null) {
            return getTopSkus(limit, from, to);
        }
        if (warehouseIds.isEmpty()) {
            return List.of();
        }
        return toTopSkuResponses(salesOrderItemRepository.findTopSkusBetweenInWarehouses(from, to, warehouseIds, limit));
    }

    private List<TopSkuResponse> toTopSkuResponses(List<SalesOrderItemRepository.TopSkuView> rows) {
        Map<UUID, String> productNamesById = productRepository.findAllById(rows.stream()
                        .map(SalesOrderItemRepository.TopSkuView::getProductId)
                        .filter(id -> id != null)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        return rows.stream()
                .map(v -> new TopSkuResponse(
                        v.getProductId(),
                        v.getProductSku(),
                        productNamesById.get(v.getProductId()),
                        v.getTotalQty(),
                        v.getTotalRevenue()))
                .toList();
    }

    ReportPeriodRange resolvePeriod(String rawPeriod, Integer selectedYear, LocalDate customFromDate,
            LocalDate customToDate) {
        String period = rawPeriod == null || rawPeriod.isBlank() ? "30d" : rawPeriod;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        if (customFromDate != null || customToDate != null) {
            LocalDate fromDate = customFromDate == null ? today.minusDays(29) : customFromDate;
            LocalDate inclusiveToDate = customToDate == null ? today : customToDate;
            if (inclusiveToDate.isBefore(fromDate)) {
                LocalDate swap = fromDate;
                fromDate = inclusiveToDate;
                inclusiveToDate = swap;
            }
            LocalDate exclusiveToDate = inclusiveToDate.plusDays(1);
            return new ReportPeriodRange(
                    fromDate.atStartOfDay(zone).toOffsetDateTime(),
                    exclusiveToDate.atStartOfDay(zone).toOffsetDateTime(),
                    fromDate,
                    exclusiveToDate,
                    false);
        }

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

    record ReportPeriodRange(
            OffsetDateTime from,
            OffsetDateTime to,
            LocalDate fromDate,
            LocalDate toDate,
            boolean yearly) {
    }
}
