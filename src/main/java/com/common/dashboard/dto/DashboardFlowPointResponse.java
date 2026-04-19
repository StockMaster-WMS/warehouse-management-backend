package com.common.dashboard.dto;

import java.time.LocalDate;

public record DashboardFlowPointResponse(
        LocalDate date,
        String name,
        long inbound,
        long outbound
) {
}
