package com.outbound_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "sales_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrder {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "so_number", unique = true, nullable = false, length = 30)
    private String soNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> shippingAddress;

    /** Cross-service reference to warehouse-service */
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Builder.Default
    @Column(name = "priority")
    private Short priority = 5;

    @Builder.Default
    @Column(name = "status", length = 25)
    private String status = "PENDING";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
