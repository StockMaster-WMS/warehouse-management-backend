package com.inbound_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PoItemResponse(
        UUID id,
        UUID purchaseOrderId,
        Short lineNumber,
        UUID productId,
        String productSku,
        String productName,
        Integer orderedQty,
        Integer receivedQty,
        BigDecimal unitPrice
) {
}
