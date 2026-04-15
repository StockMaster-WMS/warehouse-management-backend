package com.inbound_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "putaway_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayTask {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Dòng đơn nhập sinh ra nhiệm vụ putaway (nullable cho bản ghi cũ) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_item_id")
    private PoItem poItem;

    /** Phiếu nhập kho sinh ra nhiệm vụ putaway */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_receipt_id")
    private InboundReceipt inboundReceipt;

    /** Cross-service reference to product-service */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "qty_to_putaway", nullable = false)
    private Integer qtyToPutaway;

    /** Cross-service reference to warehouse-service */
    @Column(name = "suggested_location_id")
    private UUID suggestedLocationId;

    /** Cross-service reference to warehouse-service */
    @Column(name = "actual_location_id")
    private UUID actualLocationId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private PutawayStatus status = PutawayStatus.PENDING;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
    }
}
