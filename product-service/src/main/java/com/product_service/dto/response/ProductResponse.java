package com.product_service.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String barcodeEan13,
        String name,
        UUID categoryId,
        UUID primarySupplierId,
        String baseUnit,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal volumeCm3,
        Integer minStockQty,
        Boolean isLotTracked,
        Boolean isExpiryTracked,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID createdBy
) {
}