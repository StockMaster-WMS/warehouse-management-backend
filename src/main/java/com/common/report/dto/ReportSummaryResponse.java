package com.common.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReportSummaryResponse(
    BigDecimal totalRevenue,
    long totalOrders,
    double completionRate,
    List<RevenueTrendResponse> revenueTrend,
    List<TopSkuResponse> topSkus
) {}
