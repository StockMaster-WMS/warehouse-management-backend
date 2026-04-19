package com.common.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        List<DashboardMetricResponse> metrics,
        List<DashboardFlowPointResponse> flow,
        List<DashboardNoticeResponse> notices
) {
}
