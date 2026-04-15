package com.common.api.stock;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Lệnh điều chỉnh tồn kho (dùng nội bộ giữa warehouse-service và các service gọi Feign).
 */
public record StockAdjustCommand(
        @NotNull UUID warehouseId,
        @NotNull UUID locationId,
        @NotNull UUID productId,
        String lotNumber,
        int qtyDelta,
        String idempotencyKey,
        String referenceType,
        UUID referenceId
) {

    public StockAdjustCommand(UUID warehouseId, UUID locationId, UUID productId, String lotNumber, int qtyDelta) {
        this(warehouseId, locationId, productId, lotNumber, qtyDelta, null, null, null);
    }
}
