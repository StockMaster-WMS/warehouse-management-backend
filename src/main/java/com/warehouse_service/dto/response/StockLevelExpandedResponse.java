package com.warehouse_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.product_service.dto.response.ProductSummaryResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockLevelExpandedResponse(
        UUID id,
        UUID warehouseId,
        UUID locationId,
        UUID productId,
        String lotNumber,
        LocalDate expiryDate,
        Integer qtyOnHand,
        Integer qtyReserved,
        Integer qtyAvailable,
        OffsetDateTime updatedAt,
        WarehouseSummary warehouse,
        LocationSummary location,
        ProductSummaryResponse product
) {
}

