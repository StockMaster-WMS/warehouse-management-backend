package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdatePurchaseOrderRequest(
        @NotBlank(message = "PO number is required")
        @Size(max = 30, message = "PO number must not exceed 30 characters")
        String poNumber,

        @NotNull(message = "Supplier id is required")
        UUID supplierId,

        @NotNull(message = "Warehouse id is required")
        UUID warehouseId,

        String status,

        @NotNull(message = "Order date is required")
        LocalDate orderDate,

        LocalDate expectedDate,
        BigDecimal totalAmount
) {
}