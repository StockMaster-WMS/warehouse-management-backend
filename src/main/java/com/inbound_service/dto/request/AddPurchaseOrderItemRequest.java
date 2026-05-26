package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record AddPurchaseOrderItemRequest(
        Short lineNumber,

        @NotNull(message = "San pham khong duoc de trong")
        UUID productId,

        @NotBlank(message = "SKU khong duoc de trong")
        @Size(max = 50, message = "SKU khong duoc vuot qua 50 ky tu")
        String productSku,

        @Size(max = 255, message = "Ten san pham khong duoc vuot qua 255 ky tu")
        String productName,

        @NotNull(message = "So luong dat khong duoc de trong")
        @Positive(message = "So luong dat phai lon hon 0")
        Integer orderedQty,

        @NotNull(message = "Don gia khong duoc de trong")
        BigDecimal unitPrice
) {
}
