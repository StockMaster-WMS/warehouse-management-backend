package com.inbound_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    /** Cross-service reference to product-service (supplier) */
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    /** Cross-service reference to warehouse-service */
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "DRAFT";

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Builder.Default
    @Column(name = "total_amount", precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
