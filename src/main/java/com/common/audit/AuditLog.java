package com.common.audit;

import com.common.util.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 80)
    private String serviceName;

    @Column(name = "module", nullable = false, length = 80)
    private String module;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "action", nullable = false, length = 160)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "actor_email", length = 180)
    private String actorEmail;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "before_snapshot", columnDefinition = "text")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", columnDefinition = "text")
    private String afterSnapshot;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidUtils.uuidV7();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
