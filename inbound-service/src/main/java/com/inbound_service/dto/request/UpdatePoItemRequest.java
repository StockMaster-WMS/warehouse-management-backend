package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdatePoItemRequest(
        @NotNull(message = "PO không được để trống")
        UUID purchaseOrderId,

        @NotNull(message = "So dong không được để trống")
        Short lineNumber,

        @NotNull(message = "San pham không được để trống")
        UUID productId,

        @NotBlank(message = "SKU không được để trống")
        @Size(max = 50, message = "SKU không được vượt quá 50 ký tự")
        String productSku,

        @NotNull(message = "So luong dat không được để trống")
        Integer orderedQty,

        Integer receivedQty,
        BigDecimal unitPrice
) {
}