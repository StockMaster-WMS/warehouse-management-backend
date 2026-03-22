package com.warehouse_service.dto.response;

public record WarehouseSummaryResponse(
        long totalWarehouses,
        long activeWarehouses,
        long inactiveWarehouses,
        long warehousesWithStock
) {
}
