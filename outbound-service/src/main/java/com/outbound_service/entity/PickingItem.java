package com.outbound_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "picking_items",
        indexes = @Index(name = "idx_pick_items_location", columnList = "location_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "so_item_id", nullable = false)
    private SalesOrderItem soItem;

    /** Cross-service reference to product-service */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Cross-service reference to warehouse-service */
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    /** Must match warehouse stock_levels.lot_number for reserve/adjust */
    @Builder.Default
    @Column(name = "lot_number", length = 60, nullable = false)
    private String lotNumber = "";

    @Column(name = "qty_to_pick", nullable = false)
    private Integer qtyToPick;

    @Builder.Default
    @Column(name = "qty_picked")
    private Integer qtyPicked = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private PickingItemStatus status = PickingItemStatus.PENDING;

    @Column(name = "pick_sequence")
    private Integer pickSequence;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
    }
}
