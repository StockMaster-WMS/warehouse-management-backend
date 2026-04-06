package com.inbound_service.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command gửi sang product-service để tạo sản phẩm mới.
 * Trường khớp với CreateProductRequest bên product-service.
 */
public record CreateProductCommand(
        String barcodeEan13,
        String name,
        UUID categoryId,
        UUID primarySupplierId,
        String baseUnit,
        BigDecimal weightKg,
        Integer minStockQty,
        Boolean isLotTracked,
        Boolean isExpiryTracked,
        String status,
        UUID createdBy
) {
}
