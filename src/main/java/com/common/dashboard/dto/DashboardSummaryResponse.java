package com.common.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        List<DashboardMetricResponse> metrics,
        DashboardOperationsResponse operations,
        List<DashboardFlowPointResponse> flow,
        List<DashboardNoticeResponse> notices,
        List<DashboardActivityResponse> recentActivities
) {
}
