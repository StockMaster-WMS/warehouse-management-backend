package com.warehouse_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CycleCountResponse(
    UUID id,
    String countNumber,
    UUID warehouseId,
    String warehouseName,
    String status,
    String description,
    String scope,
    String scopeValue,
    OffsetDateTime scheduledAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    UUID createdBy,
    UUID approvedBy,
    OffsetDateTime createdAt,
    List<LineResponse> lines
) {
    public record LineResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSku,
        UUID locationId,
        String locationCode,
        String lotNumber,
        Integer systemQty,
        Integer countedQty,
        Integer discrepancy,
        String status,
        String notes
    ) {}
}
