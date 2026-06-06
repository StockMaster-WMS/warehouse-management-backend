package com.inbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PutawayTaskResponse(
        UUID id,
        UUID poItemId,
        UUID inboundReceiptId,
        UUID warehouseId,
        UUID productId,
        Integer qtyToPutaway,
        UUID suggestedLocationId,
        UUID actualLocationId,
        String status,
        UUID assignedTo,
        OffsetDateTime completedAt
) {
}
