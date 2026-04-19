package com.outbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record UpdateSalesOrderRequest(
        @NotBlank(message = "Mã đơn xuất không được để trống")
        @Size(max = 30, message = "Mã đơn xuất không được vượt quá 30 ký tự")
        String soNumber,

        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(max = 200, message = "Tên khách hàng không được vượt quá 200 ký tự")
        String customerName,

        @NotNull(message = "Địa chỉ giao hàng không được để trống")
        @NotEmpty(message = "Địa chỉ giao hàng không được rỗng")
        Map<String, Object> shippingAddress,

        @NotNull(message = "Mã kho không được để trống")
        UUID warehouseId,

        Short priority,
        String status
) {
}