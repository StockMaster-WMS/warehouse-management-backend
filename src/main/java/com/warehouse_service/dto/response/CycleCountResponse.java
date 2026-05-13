package com.warehouse_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CycleCountResponse(
    UUID id,
    UUID warehouseId,
    String status,
    String description,
    OffsetDateTime scheduledAt,
    OffsetDateTime completedAt,
    UUID createdBy,
    UUID approvedBy,
    OffsetDateTime createdAt,
    List<ItemResponse> items
) {
    public record ItemResponse(
        UUID id,
        UUID productId,
        UUID locationId,
        String lotNumber,
        Integer systemQty,
        Integer countedQty,
        Integer discrepancy,
        String status,
        String notes
    ) {}
}
