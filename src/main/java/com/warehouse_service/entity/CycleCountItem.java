package com.warehouse_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "cycle_count_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_count_id", nullable = false)
    private CycleCount cycleCount;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "counted_qty")
    private Integer countedQty;

    @Column(name = "discrepancy")
    private Integer discrepancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25)
    @Builder.Default
    private ItemStatus status = ItemStatus.PENDING;

    private String notes;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (status == null) status = ItemStatus.PENDING;
    }

    public enum ItemStatus {
        PENDING, COUNTED, ADJUSTED
    }
}
