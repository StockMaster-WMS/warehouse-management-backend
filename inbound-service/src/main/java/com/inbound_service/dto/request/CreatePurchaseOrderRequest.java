package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotBlank(message = "Mã đơn nhập không được để trống")
        @Size(max = 30, message = "Mã đơn nhập không được vượt quá 30 ký tự")
        String poNumber,

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