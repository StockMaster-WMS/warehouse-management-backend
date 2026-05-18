package com.common.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String severity,
        String title,
        String message,
        String targetType,
        UUID targetId,
        boolean read,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {
}
