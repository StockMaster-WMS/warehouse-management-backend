package com.inbound_service.dto.response;

import java.util.UUID;

public record InboundReceiptItemResponse(
        UUID id,
        UUID poItemId,
        UUID productId,
        String productSku,
        Integer receivedQty,
        UUID locationId,
        String note
) {
}
