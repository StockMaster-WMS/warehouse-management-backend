package com.common.dashboard.dto;

public record DashboardMetricResponse(
        String key,
        String label,
        long value,
        String trend,
        String tone
) {
}
