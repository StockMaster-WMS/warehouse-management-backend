package com.inbound_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

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
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

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
    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
