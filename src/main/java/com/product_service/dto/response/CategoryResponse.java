package com.product_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        @Schema(example = "7a9d0f4e-6da8-44b7-bb86-4d6e2f954a75")
        UUID id,

        @Schema(example = "DM-001")
        String code,

        @Schema(example = "Thuc pham dong goi")
        String name,

        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID parentId,

        @Schema(example = "THUC_PHAM/DONG_GOI")
        String path,

        @Schema(example = "1")
        Short level,

        @Schema(example = "true")
        Boolean isActive,

        @Schema(example = "2026-03-14T08:30:00Z")
        OffsetDateTime createdAt
) {
}
