package com.warehouse_service.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        UUID warehouseId,
        String code,
        String zone,
        String aisle,
        String rack,
        Short level,
        String bin,
        String locationType,
        BigDecimal maxWeightKg,
        BigDecimal maxVolumeCm3,
        Integer pickSequence,
        String status,
        Boolean isActive,
        Boolean isColdZone,
        Boolean isHazmatZone,
        Boolean isHeavyZone,
        OffsetDateTime createdAt
) {
}