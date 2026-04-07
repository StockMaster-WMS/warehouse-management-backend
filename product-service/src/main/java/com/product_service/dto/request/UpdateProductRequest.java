package com.product_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank(message = "SKU không được để trống")
        @Size(max = 50, message = "SKU không được vượt quá 50 ký tự")
        @Schema(example = "SP-0001")
        String sku,

        @Size(max = 13, message = "Mã vạch không được vượt quá 13 ký tự")
        @Schema(example = "8938505974192")
        String barcodeEan13,

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự")
        @Schema(example = "Banh quy bo")
        String name,

        @NotNull(message = "Mã danh mục không được để trống")
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID categoryId,

        @Schema(example = "1d9f9f8b-9a6c-4c3e-b3e5-9f7e7a9f1234")
        UUID primarySupplierId,

        @NotBlank(message = "Đơn vị cơ bản không được để trống")
        @Size(max = 20, message = "Đơn vị cơ bản không được vượt quá 20 ký tự")
        @Schema(example = "goi")
        String baseUnit,

        @Schema(example = "0.45")
        BigDecimal weightKg,
        @Schema(example = "1296")
        BigDecimal volumeCm3,
        @Schema(example = "20")
        Integer minStockQty,
        @Schema(example = "true")
        Boolean isLotTracked,
        @Schema(example = "true")
        Boolean isExpiryTracked,
        @Schema(example = "false")
        Boolean isFrozen,
        @Schema(example = "false")
        Boolean isFragile,
        @Schema(example = "false")
        Boolean isHazmat,
        @Schema(example = "false")
        Boolean isHeavy,
        @Schema(example = "ACTIVE")
        String status
) {
}
