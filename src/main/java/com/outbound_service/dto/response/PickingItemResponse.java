package com.outbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PickingItemResponse(
        UUID id,
        UUID soItemId,
        UUID productId,
        UUID locationId,
        String lotNumber,
        Integer qtyToPick,
        Integer qtyPicked,
        String status,
        Integer pickSequence,
        String salesOrderNumber,
        String productSku,
        String productName,
        String barcodeEan13,
        String locationCode,
        String locationName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        UUID assigneeId,
        OffsetDateTime completedAt
) {
}
