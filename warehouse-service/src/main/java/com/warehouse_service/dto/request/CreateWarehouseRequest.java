package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank(message = "Warehouse code is required")
        @Size(max = 20, message = "Warehouse code must not exceed 20 characters")
        String code,

        @NotBlank(message = "Warehouse name is required")
        @Size(max = 150, message = "Warehouse name must not exceed 150 characters")
        String name,

        String address,
        String timezone,
        Boolean isActive
) {
}