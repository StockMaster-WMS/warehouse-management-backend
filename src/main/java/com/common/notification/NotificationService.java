package com.common.notification;

import com.auth_service.entity.UserAccount;
import com.auth_service.repository.UserRepository;
import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public PagedResponse<NotificationResponse> findMine(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipient_IdAndReadFalse(userId, pageable)
                : notificationRepository.findByRecipient_Id(userId, pageable);
        List<NotificationResponse> rows = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PagedResponse<>(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public long countUnread(UUID userId) {
        return notificationRepository.countByRecipient_IdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipient_Id(notificationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(OffsetDateTime.now());
        }
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId, OffsetDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponse create(CreateNotificationCommand command) {
        if (command.recipientUserId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "recipientUserId là bắt buộc");
        }
        UserAccount recipient = userRepository.findById(command.recipientUserId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người nhận thông báo"));

        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(command.type() == null ? NotificationType.SYSTEM_ALERT : command.type())
                .severity(command.severity() == null ? NotificationSeverity.INFO : command.severity())
                .title(requiredText(command.title(), "Thông báo"))
                .message(requiredText(command.message(), "Bạn có thông báo mới"))
                .targetType(blankToNull(command.targetType()))
                .targetId(command.targetId())
                .build();
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createIfNoUnread(CreateNotificationCommand command) {
        if (command.recipientUserId() == null || command.type() == null
                || !StringUtils.hasText(command.targetType()) || command.targetId() == null) {
            create(command);
            return true;
        }
        boolean exists = notificationRepository.existsByRecipient_IdAndTypeAndTargetTypeAndTargetIdAndReadFalse(
                command.recipientUserId(), command.type(), command.targetType().trim(), command.targetId());
        if (exists) {
            return false;
        }
        create(command);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createForRoles(Collection<String> roleCodes, NotificationType type, NotificationSeverity severity,
            String title, String message, String targetType, UUID targetId) {
        Set<String> normalizedRoleCodes = normalizeRoleCodes(roleCodes);
        if (normalizedRoleCodes.isEmpty()) {
            return 0;
        }

        List<UserAccount> recipients = userRepository.findActiveByRoleCodes(normalizedRoleCodes);
        for (UserAccount recipient : recipients) {
            create(new CreateNotificationCommand(
                    recipient.getId(), type, severity, title, message, targetType, targetId));
        }
        return recipients.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createForRolesIfNoUnread(Collection<String> roleCodes, NotificationType type, NotificationSeverity severity,
            String title, String message, String targetType, UUID targetId) {
        Set<String> normalizedRoleCodes = normalizeRoleCodes(roleCodes);
        if (normalizedRoleCodes.isEmpty()) {
            return 0;
        }

        int created = 0;
        List<UserAccount> recipients = userRepository.findActiveByRoleCodes(normalizedRoleCodes);
        for (UserAccount recipient : recipients) {
            if (createIfNoUnread(new CreateNotificationCommand(
                    recipient.getId(), type, severity, title, message, targetType, targetId))) {
                created++;
            }
        }
        return created;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getSeverity().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    private static String requiredText(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static Set<String> normalizeRoleCodes(Collection<String> roleCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (roleCodes == null) {
            return normalized;
        }
        for (String roleCode : roleCodes) {
            if (StringUtils.hasText(roleCode)) {
                normalized.add(roleCode.trim().toUpperCase());
            }
        }
        return normalized;
    }
}
