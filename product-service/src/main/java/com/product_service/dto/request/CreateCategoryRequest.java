package com.product_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 120, message = "Tên danh mục không được vượt quá 120 ký tự")
        @Schema(example = "Thuc pham dong goi")
        String name,

        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID parentId,

        @Schema(example = "true")
        Boolean isActive
) {
}
