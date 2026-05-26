package com.inbound_service.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record SupplierReturnLocationOptionResponse(
        UUID stockLevelId,
        UUID locationId,
        String locationCode,
        String zone,
        UUID productId,
        String lotNumber,
        LocalDate expiryDate,
        Integer qtyOnHand,
        Integer qtyReserved,
        Integer qtyAvailable,
        Integer maxReturnQty
) {
}
