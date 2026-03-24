package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateStockLevelRequest(
        @NotNull(message = "Kho không được để trống")
        UUID warehouseId,

        @NotNull(message = "Vị trí không được để trống")
        UUID locationId,

        @NotNull(message = "Sản phẩm không được để trống")
        UUID productId,

        @Size(max = 60, message = "Số lô không được vượt quá 60 ký tự")
        String lotNumber,

        LocalDate expiryDate,

        @NotNull(message = "Số lượng tồn không được để trống")
        Integer qtyOnHand,

        Integer qtyReserved
) {
}