package com.common.dashboard.dto;

public record DashboardNoticeResponse(
        String title,
        String description,
        String type,
        String time
) {
}
