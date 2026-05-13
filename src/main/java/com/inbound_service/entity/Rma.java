package com.inbound_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rma_headers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rma {

    @Id
    private UUID id;

    @Column(name = "rma_number", unique = true, nullable = false, length = 30)
    private String rmaNumber;

    @Column(name = "sales_order_id")
    private UUID salesOrderId;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25)
    @Builder.Default
    private RmaStatus status = RmaStatus.REQUESTED;

    private String reason;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RmaItem> items;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = RmaStatus.REQUESTED;
    }

    public enum RmaStatus {
        REQUESTED, RECEIVED, COMPLETED, CANCELLED
    }
}
