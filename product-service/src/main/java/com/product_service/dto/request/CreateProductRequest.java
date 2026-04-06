package com.product_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
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
        @Schema(example = "20")
        Integer minStockQty,
        @Schema(example = "true")
        Boolean isLotTracked,
        @Schema(example = "true")
        Boolean isExpiryTracked,
        @Schema(example = "ACTIVE")
        String status,

        @NotNull(message = "Người tạo không được để trống")
        @Schema(example = "2e7c6d35-93ff-4d6f-8ec5-4c0900d8c971")
        UUID createdBy
) {
}
