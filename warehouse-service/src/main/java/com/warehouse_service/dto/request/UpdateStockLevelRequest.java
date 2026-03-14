package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateStockLevelRequest(
        @NotNull(message = "Kho không được để trống")
        UUID warehouseId,

        @NotNull(message = "Vi tri không được để trống")
        UUID locationId,

        @NotNull(message = "San pham không được để trống")
        UUID productId,

        @Size(max = 60, message = "So lo khong duoc vuot qua 60 ky tu")
        String lotNumber,

        LocalDate expiryDate,

        @NotNull(message = "So luong ton khong duoc de trong")
        Integer qtyOnHand,

        Integer qtyReserved
) {
}