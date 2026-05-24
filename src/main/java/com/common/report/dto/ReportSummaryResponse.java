package com.common.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportSummaryResponse(
    BigDecimal totalRevenue,
    long totalOrders,
    long activeOrders,
    long shippedOrders,
    double completionRate,
    LocalDate fromDate,
    LocalDate toDate,
    UUID warehouseId,
    String warehouseName,
    List<RevenueTrendResponse> revenueTrend,
    List<TopSkuResponse> topSkus
) {}
