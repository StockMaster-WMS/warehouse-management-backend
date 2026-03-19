package com.product_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products",
        indexes = {
                @Index(name = "idx_products_sku",      columnList = "sku"),
                @Index(name = "idx_products_category", columnList = "category_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sku", unique = true, nullable = false, length = 50)
    private String sku;

    @Column(name = "barcode_ean13", unique = true, length = 13)
    private String barcodeEan13;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_supplier_id")
    private Supplier primarySupplier;

    @Column(name = "base_unit", nullable = false, length = 20)
    private String baseUnit;

    @Column(name = "weight_kg", precision = 10, scale = 4)
    private BigDecimal weightKg;

    @Column(name = "length_cm", precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 8, scale = 2)
    private BigDecimal heightCm;

    /** Computed column: length_cm * width_cm * height_cm */
    @Column(name = "volume_cm3", precision = 12, scale = 4, insertable = false, updatable = false)
    private BigDecimal volumeCm3;

    @Column(name = "min_stock_qty")
    @Builder.Default
    private Integer minStockQty = 0;

    @Column(name = "is_lot_tracked")
    @Builder.Default
    private Boolean isLotTracked = false;

    @Column(name = "is_expiry_tracked")
    @Builder.Default
    private Boolean isExpiryTracked = false;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) id = UuidUtils.uuidV7();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (createdBy == null) {
            createdBy = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
