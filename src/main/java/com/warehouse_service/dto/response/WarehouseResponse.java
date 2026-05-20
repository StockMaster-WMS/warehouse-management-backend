package com.warehouse_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String name,
        String address,
        String managerName,
        List<WarehouseManagerResponse> managers,
        String timezone,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
