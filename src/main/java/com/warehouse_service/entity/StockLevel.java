package com.warehouse_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_levels",
        indexes = @Index(name = "idx_stock_product", columnList = "product_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_stock_level",
                columnNames = {"location_id", "product_id", "lot_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevel {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /** Cross-service reference to product-service */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Builder.Default
    @Column(name = "lot_number", length = 60, nullable = false)
    private String lotNumber = "";

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "qty_on_hand", nullable = false)
    private Integer qtyOnHand;

    @Builder.Default
    @Column(name = "qty_reserved")
    private Integer qtyReserved = 0;

    /** Computed column: qty_on_hand - qty_reserved */
    @Column(name = "qty_available", insertable = false, updatable = false)
    private Integer qtyAvailable;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void preUpdate() {
        if (id == null) id = UuidUtils.uuidV7();
        updatedAt = OffsetDateTime.now();
    }
}
