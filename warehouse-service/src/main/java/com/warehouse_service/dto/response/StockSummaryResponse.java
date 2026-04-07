package com.warehouse_service.dto.response;

public record StockSummaryResponse(
        long totalSkus,
        long totalQtyOnHand,
        long totalQtyReserved,
        long totalQtyAvailable,
        long lowStockCount,
        long nearExpiryCount
) {
}
