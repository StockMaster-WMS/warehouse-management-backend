package com.inbound_service.dto.response;

import java.util.UUID;

public record InboundPrintItemResponse(
        Short lineNumber,
        UUID productId,
        String productSku,
        String productName,
        String unit,
        Integer orderedQty,
        Integer receivedQty,
        String note
) {
}
