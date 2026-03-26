package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotNull(message = "Mã nhà cung cấp không được để trống")
        UUID supplierId,

        @NotNull(message = "Mã kho không được để trống")
        UUID warehouseId,

        String status,

        @NotNull(message = "Ngày đặt hàng không được để trống")
        LocalDate orderDate,

        LocalDate expectedDate,
        BigDecimal totalAmount
) {
}
