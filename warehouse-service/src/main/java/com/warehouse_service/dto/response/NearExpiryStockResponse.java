package com.warehouse_service.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record NearExpiryStockResponse(
        UUID id,
        UUID warehouseId,
        String warehouseCode,
        UUID locationId,
        String locationCode,
        UUID productId,
        String lotNumber,
        LocalDate expiryDate,
        long daysLeft,
        Integer qtyOnHand,
        Integer qtyReserved,
        Integer qtyAvailable
) {
}
