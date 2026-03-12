package com.inbound_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_events",
        indexes = @Index(name = "idx_outbox_pending", columnList = "status, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
