package com.product_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank(message = "SKU is required")
        @Size(max = 50, message = "SKU must not exceed 50 characters")
        String sku,

        @Size(max = 13, message = "Barcode must not exceed 13 characters")
        String barcodeEan13,

        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,

        @NotNull(message = "Category id is required")
        UUID categoryId,

        UUID primarySupplierId,

        @NotBlank(message = "Base unit is required")
        @Size(max = 20, message = "Base unit must not exceed 20 characters")
        String baseUnit,

        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        Integer minStockQty,
        Boolean isLotTracked,
        Boolean isExpiryTracked,
        String status
) {
}