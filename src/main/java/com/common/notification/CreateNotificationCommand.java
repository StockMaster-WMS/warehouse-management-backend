package com.common.notification;

import java.util.UUID;

public record CreateNotificationCommand(
        UUID recipientUserId,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String message,
        String targetType,
        UUID targetId
) {
}
