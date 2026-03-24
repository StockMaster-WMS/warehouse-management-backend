package com.inbound_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WarehouseStockData(
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
