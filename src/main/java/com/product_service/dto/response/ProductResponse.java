package com.product_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
                @Schema(example = "7a9d0f4e-6da8-44b7-bb86-4d6e2f954a75") UUID id,
                @Schema(example = "SP-0001") String sku,
                @Schema(example = "8938505974192") String barcodeEan13,
                @Schema(example = "Banh quy bo") String name,
                @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID categoryId,
                @Schema(example = "Thực phẩm") String categoryName,
                @Schema(example = "1d9f9f8b-9a6c-4c3e-b3e5-9f7e7a9f1234") UUID primarySupplierId,
                @Schema(example = "Công ty TNHH XYZ") String primarySupplierName,
                @Schema(example = "goi") String baseUnit,
                @Schema(example = "0.45") BigDecimal weightKg,
                @Schema(example = "1296") BigDecimal volumeCm3,
                @Schema(example = "20") Integer minStockQty,
                @Schema(example = "true") Boolean isLotTracked,
                @Schema(example = "true") Boolean isExpiryTracked,
                @Schema(example = "false") Boolean isFrozen,
                @Schema(example = "false") Boolean isFragile,
                @Schema(example = "false") Boolean isHazmat,
                @Schema(example = "false") Boolean isHeavy,
                @Schema(example = "ACTIVE") String status,
                @Schema(example = "2026-03-14T08:30:00Z") OffsetDateTime createdAt,
                @Schema(example = "2026-03-14T09:15:00Z") OffsetDateTime updatedAt,
                @Schema(example = "2e7c6d35-93ff-4d6f-8ec5-4c0900d8c971") UUID createdBy,
                @Schema(example = "Nguyen Van An") String createdByName) {
}
