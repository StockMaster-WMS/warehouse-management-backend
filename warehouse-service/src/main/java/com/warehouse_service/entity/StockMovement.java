package com.warehouse_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Builder.Default
    @Column(name = "lot_number", length = 60)
    private String lotNumber = "";

    @Column(name = "movement_type", nullable = false, length = 30)
    private String movementType;

    @Column(name = "qty_change", nullable = false)
    private Integer qtyChange;

    @Column(name = "qty_after", nullable = false)
    private Integer qtyAfter;

    @Builder.Default
    @Column(name = "reserved_change")
    private Integer reservedChange = 0;

    @Builder.Default
    @Column(name = "reserved_after")
    private Integer reservedAfter = 0;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reference_type", length = 60)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
