package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateCycleCountRequest(
    @NotNull(message = "Warehouse ID không được để trống")
    UUID warehouseId,

    String description,

    OffsetDateTime scheduledAt,

    UUID assignedTo,

    /**
     * SCOPE mode: WAREHOUSE, ZONE, LOCATION, PRODUCT
     * - If scope is set, items can be null/empty
     * - If scope is null, items must be provided
     */
    String scope,

    /**
     * scopeValue depends on scope:
     * - WAREHOUSE: ignored (uses warehouseId)
     * - ZONE: zone name (e.g., "A", "B", "COLD_ZONE")
     * - LOCATION: locationId
     * - PRODUCT: productId
     */
    String scopeValue,

    /**
     * Manual mode: List của items cần kiểm kê
     * - Required if scope is null
     * - Ignored if scope is set
     */
    @Valid
    List<ItemRequest> items
) {
    public record ItemRequest(
        @NotNull UUID productId,
        @NotNull UUID locationId,
        String lotNumber
    ) {}
}
