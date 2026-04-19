package com.inbound_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String poNumber,
        UUID supplierId,
        UUID warehouseId,
        String status,
        LocalDate orderDate,
        LocalDate expectedDate,
        BigDecimal totalAmount,
        OffsetDateTime createdAt
) {
}