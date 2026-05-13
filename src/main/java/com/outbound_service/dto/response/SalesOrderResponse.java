package com.outbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record SalesOrderResponse(
        UUID id,
        String soNumber,
        String customerName,
        Map<String, Object> shippingAddress,
        UUID warehouseId,
        Short priority,
        String status,
        OffsetDateTime createdAt,
        java.util.List<SalesOrderItemResponse> items
) {
}