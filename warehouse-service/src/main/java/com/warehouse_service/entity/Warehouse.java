package com.warehouse_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Builder.Default
    @Column(name = "timezone", length = 50)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
