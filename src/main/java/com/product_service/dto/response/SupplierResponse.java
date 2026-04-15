package com.product_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
        @Schema(example = "7a9d0f4e-6da8-44b7-bb86-4d6e2f954a75")
        UUID id,

        @Schema(example = "NCC-001")
        String code,

        @Schema(example = "Cong ty TNHH ABC")
        String name,

        @Schema(example = "0312345678")
        String taxCode,

        @Schema(example = "Nguyen Van A")
        String contactName,

        @Schema(example = "0901234567")
        String contactPhone,

        @Schema(example = "nccabc@example.com")
        String contactEmail,

        @Schema(example = "123 Nguyen Trai, Quan 1, TP.HCM")
        String address,

        @Schema(example = "30")
        Short paymentTerms,

        @Schema(example = "7")
        Short leadTimeDays,

        @Schema(example = "active")
        String status,

        @Schema(example = "2026-03-14T08:30:00Z")
        OffsetDateTime createdAt,

        @Schema(example = "2026-03-14T09:15:00Z")
        OffsetDateTime updatedAt
) {
}
