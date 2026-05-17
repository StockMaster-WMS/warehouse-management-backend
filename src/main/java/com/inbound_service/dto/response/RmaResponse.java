package com.inbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RmaResponse(
    UUID id,
    String rmaNumber,
    UUID salesOrderId,
    String customerName,
    String status,
    String reason,
    UUID warehouseId,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt,
    List<ItemResponse> items
) {
    public record ItemResponse(
        UUID id,
        UUID productId,
        Integer expectedQty,
        Integer receivedQty,
        UUID receivedLocationId,
        String lotNumber,
        String condition,
        String notes
    ) {}
}
