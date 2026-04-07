package com.warehouse_service.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID warehouseId,
        String warehouseCode,
        UUID locationId,
        String locationCode,
        UUID productId,
        String lotNumber,
        String movementType,
        Integer qtyChange,
        Integer qtyAfter,
        Integer reservedChange,
        Integer reservedAfter,
        String reason,
        String referenceType,
        UUID referenceId,
        String createdBy,
        OffsetDateTime createdAt
) {
}
