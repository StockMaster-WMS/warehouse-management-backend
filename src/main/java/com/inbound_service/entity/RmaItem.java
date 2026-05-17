package com.inbound_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "rma_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RmaItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rma_id", nullable = false)
    private Rma rma;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "expected_qty", nullable = false)
    private Integer expectedQty;

    @Column(name = "received_qty")
    private Integer receivedQty;

    @Column(name = "received_location_id")
    private UUID receivedLocationId;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "condition", length = 50)
    private String condition;

    private String notes;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (receivedQty == null) receivedQty = 0;
    }
}
