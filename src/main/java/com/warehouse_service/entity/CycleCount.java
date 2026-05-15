package com.warehouse_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCount {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25)
    @Builder.Default
    private CycleCountStatus status = CycleCountStatus.PENDING;

    private String description;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "cycleCount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CycleCountItem> items;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = CycleCountStatus.PENDING;
    }

    public enum CycleCountStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
