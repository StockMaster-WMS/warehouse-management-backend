package com.inbound_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductData(
        UUID id,
        String sku,
        String barcodeEan13,
        String name,
        UUID categoryId,
        String categoryName,
        UUID primarySupplierId,
        String baseUnit,
        BigDecimal weightKg,
        BigDecimal volumeCm3,
        Integer minStockQty,
        Boolean isLotTracked,
        Boolean isExpiryTracked,
        String status
) {
}
