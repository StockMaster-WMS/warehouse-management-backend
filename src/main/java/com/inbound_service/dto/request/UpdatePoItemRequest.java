package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdatePoItemRequest(
        @NotNull(message = "PO không được để trống")
        UUID purchaseOrderId,

        @NotNull(message = "Số dòng không được để trống")
        Short lineNumber,

        @NotNull(message = "Sản phẩm không được để trống")
        UUID productId,

        @NotBlank(message = "SKU không được để trống")
        @Size(max = 50, message = "SKU không được vượt quá 50 ký tự")
        String productSku,

        @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự")
        String productName,

        @NotNull(message = "Số lượng đặt không được để trống")
        Integer orderedQty,

        BigDecimal unitPrice
) {
}
