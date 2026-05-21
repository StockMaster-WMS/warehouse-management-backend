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

    @Builder.Default
    @Column(name = "return_type", length = 20)
    private String returnType = "CUSTOMER";

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(name = "customer_id")
    private UUID customerId;

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

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RmaItem> items;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = RmaStatus.REQUESTED;
        if (returnType == null || returnType.isBlank()) returnType = "CUSTOMER";
    }

    public enum RmaStatus {
        REQUESTED, RECEIVED, APPROVED, REJECTED, COMPLETED, CANCELLED
    }
}
