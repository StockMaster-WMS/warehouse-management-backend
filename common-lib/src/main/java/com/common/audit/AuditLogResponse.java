package com.common.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String serviceName,
        String module,
        String actionType,
        String action,
        String entityType,
        UUID entityId,
        String entityName,
        UUID actorId,
        String actorName,
        String actorEmail,
        String reason,
        String beforeSnapshot,
        String afterSnapshot,
        String metadata,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt
) {
}
