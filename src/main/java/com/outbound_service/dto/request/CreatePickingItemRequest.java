package com.outbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePickingItemRequest(
        @NotNull(message = "SO item không được để trống")
        UUID soItemId,

        @NotNull(message = "Sản phẩm không được để trống")
        UUID productId,

        @NotNull(message = "Vị trí không được để trống")
        UUID locationId,

        @NotNull(message = "Số lượng cần pick không được để trống")
        Integer qtyToPick,

        Integer qtyPicked,

        @Size(max = 20, message = "Trạng thái không được vượt quá 20 ký tự")
        @NotBlank(message = "Trạng thái không được để trống")
        String status,
        Integer pickSequence,
        /** Lô tại warehouse; null hoặc rỗng = không lô */
        String lotNumber
) {
}