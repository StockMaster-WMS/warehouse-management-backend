package com.common.api.stock;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Điều chỉnh số lượng giữ chỗ (qty_reserved). delta &gt; 0: giữ thêm; &lt; 0: nhả chỗ.
 */
public record StockReserveCommand(
        @NotNull UUID warehouseId,
        @NotNull UUID locationId,
        @NotNull UUID productId,
        String lotNumber,
        int reservedDelta
) {
}
