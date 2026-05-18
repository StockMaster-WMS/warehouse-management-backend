package com.common.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipient_Id(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipient_IdAndReadFalse(UUID recipientUserId, Pageable pageable);

    Optional<Notification> findByIdAndRecipient_Id(UUID id, UUID recipientUserId);

    long countByRecipient_IdAndReadFalse(UUID recipientUserId);

    boolean existsByRecipient_IdAndTypeAndTargetTypeAndTargetIdAndReadFalse(
            UUID recipientUserId, NotificationType type, String targetType, UUID targetId);

    @Modifying
    @Query("""
            update Notification n
            set n.read = true, n.readAt = :readAt
            where n.recipient.id = :recipientUserId and n.read = false
            """)
    int markAllAsRead(@Param("recipientUserId") UUID recipientUserId, @Param("readAt") OffsetDateTime readAt);
}
