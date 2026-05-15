package com.common.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardActivityResponse(
    UUID id,
    String module,
    String actionType,
    String action,
    String entityName,
    String actorName,
    OffsetDateTime createdAt
) {}
