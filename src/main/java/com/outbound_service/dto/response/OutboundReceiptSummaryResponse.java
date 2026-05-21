package com.outbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboundReceiptSummaryResponse(
        UUID id,
        String soNumber,
        UUID customerId,
        String customerName,
        UUID warehouseId,
        String status,
        OffsetDateTime createdAt,
        Integer totalShippedQty,
        Integer totalReturnableQty
) {
}
