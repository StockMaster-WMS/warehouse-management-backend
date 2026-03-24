package com.outbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateSalesOrderItemRequest(
        @NotNull UUID salesOrderId,
        @NotNull Short lineNumber,
        @NotNull UUID productId,
        @NotBlank @Size(max = 50) String productSku,
        @NotNull @Positive Integer orderedQty,
        Integer shippedQty,
        BigDecimal unitPrice
) {
}
