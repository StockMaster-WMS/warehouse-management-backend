package com.warehouse_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations",
        indexes = @Index(name = "idx_locations_zone_status", columnList = "warehouse_id, zone, status"),
        uniqueConstraints = @UniqueConstraint(name = "uq_location_code", columnNames = {"warehouse_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "zone", nullable = false, length = 20)
    private String zone;

    @Column(name = "aisle", nullable = false, length = 10)
    private String aisle;

    @Column(name = "rack", nullable = false, length = 10)
    private String rack;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "bin", nullable = false, length = 10)
    private String bin;

    @Builder.Default
    @Column(name = "location_type", length = 20)
    private String locationType = "STORAGE";

    @Column(name = "max_weight_kg", precision = 10, scale = 2)
    private BigDecimal maxWeightKg;

    @Column(name = "max_volume_cm3", precision = 15, scale = 4)
    private BigDecimal maxVolumeCm3;

    @Column(name = "pick_sequence")
    private Integer pickSequence;

    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "AVAILABLE";

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidUtils.uuidV7();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
