package com.outbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePickingItemRequest(
        @NotNull(message = "SO item không được để trống")
        UUID soItemId,

        @NotNull(message = "San pham không được để trống")
        UUID productId,

        @NotNull(message = "Vi tri không được để trống")
        UUID locationId,

        @NotNull(message = "So luong can pick không được để trống")
        Integer qtyToPick,

        Integer qtyPicked,

        @Size(max = 20, message = "Trang thai không được vượt quá 20 ký tự")
        @NotBlank(message = "Trang thai không được để trống")
        String status,
        Integer pickSequence
) {
}