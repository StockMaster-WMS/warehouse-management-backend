package com.outbound_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemResponse(
        UUID id,
        UUID salesOrderId,
        Short lineNumber,
        UUID productId,
        String productSku,
        Integer orderedQty,
        Integer shippedQty,
        BigDecimal unitPrice
) {
}
