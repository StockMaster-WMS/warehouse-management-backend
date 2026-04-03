package com.inbound_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "po_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_po_line", columnNames = {"po_id", "line_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_number", nullable = false)
    private Short lineNumber;

    /** Cross-service reference to product-service */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Snapshot of SKU at time of PO creation */
    @Column(name = "product_sku", nullable = false, length = 50)
    private String productSku;

    /** Snapshot of product name at time of PO creation */
    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Builder.Default
    @Column(name = "received_qty")
    private Integer receivedQty = 0;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
    }
}
