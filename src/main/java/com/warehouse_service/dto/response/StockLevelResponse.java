package com.warehouse_service.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockLevelResponse(
        UUID id,
        UUID warehouseId,
        UUID locationId,
        UUID productId,
        String lotNumber,
        LocalDate expiryDate,
        Integer qtyOnHand,
        Integer qtyReserved,
        Integer qtyAvailable,
        OffsetDateTime updatedAt
) {
}