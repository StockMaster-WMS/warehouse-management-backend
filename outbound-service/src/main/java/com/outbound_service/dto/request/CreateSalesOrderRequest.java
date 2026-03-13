package com.outbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateSalesOrderRequest(
        @NotBlank(message = "SO number is required")
        @Size(max = 30, message = "SO number must not exceed 30 characters")
        String soNumber,

        @NotBlank(message = "Customer name is required")
        @Size(max = 200, message = "Customer name must not exceed 200 characters")
        String customerName,

        @NotNull(message = "Shipping address is required")
        @NotEmpty(message = "Shipping address must not be empty")
        Map<String, Object> shippingAddress,

        @NotNull(message = "Warehouse id is required")
        UUID warehouseId,

        Short priority,
        String status
) {
}