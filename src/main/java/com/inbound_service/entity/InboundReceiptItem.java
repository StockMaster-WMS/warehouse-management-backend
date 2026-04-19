package com.inbound_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_item_id", nullable = false)
    private PoItem poItem;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_sku", nullable = false, length = 50)
    private String productSku;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "note")
    private String note;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
    }
}
