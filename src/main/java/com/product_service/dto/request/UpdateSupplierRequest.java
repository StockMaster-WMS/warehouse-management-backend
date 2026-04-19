package com.product_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
        @NotBlank(message = "Mã nhà cung cấp không được để trống")
        @Size(max = 20, message = "Mã nhà cung cấp không được vượt quá 20 ký tự")
        @Schema(example = "NCC-001")
        String code,

        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 200, message = "Tên nhà cung cấp không được vượt quá 200 ký tự")
        @Schema(example = "Cong ty TNHH ABC")
        String name,

        @Size(max = 20, message = "Mã số thuế không được vượt quá 20 ký tự")
        @Schema(example = "0312345678")
        String taxCode,

        @Size(max = 100, message = "Tên người liên hệ không được vượt quá 100 ký tự")
        @Schema(example = "Nguyen Van A")
        String contactName,

        @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
        @Schema(example = "0901234567")
        String contactPhone,

        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        @Schema(example = "nccabc@example.com")
        String contactEmail,

        @Schema(example = "123 Nguyen Trai, Quan 1, TP.HCM")
        String address,

        @Schema(example = "30")
        Short paymentTerms,

        @Schema(example = "7")
        Short leadTimeDays,

        @Schema(example = "active")
        String status
) {
}
