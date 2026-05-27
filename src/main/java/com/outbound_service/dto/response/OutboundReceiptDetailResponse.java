package com.outbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OutboundReceiptDetailResponse(
        UUID id,
        String soNumber,
        UUID customerId,
        String customerName,
        UUID warehouseId,
        String status,
        OffsetDateTime createdAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            UUID salesOrderItemId,
            UUID productId,
            String productSku,
            String productName,
            UUID locationId,
            String locationCode,
            String lotNumber,
            Integer shippedQty,
            Integer alreadyReturnedQty,
            Integer returnableQty
    ) {
    }
}
