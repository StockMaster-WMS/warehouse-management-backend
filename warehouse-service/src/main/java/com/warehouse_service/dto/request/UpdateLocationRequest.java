package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateLocationRequest(
        @NotNull(message = "Kho không được để trống")
        UUID warehouseId,

        @NotBlank(message = "Mã vị trí không được để trống")
        @Size(max = 40, message = "Mã vị trí không được vượt quá 40 ký tự")
        String code,

        @NotBlank(message = "Zone không được để trống")
        @Size(max = 20, message = "Zone không được vượt quá 20 ký tự")
        String zone,

        @NotBlank(message = "Aisle không được để trống")
        @Size(max = 10, message = "Aisle không được vượt quá 10 ký tự")
        String aisle,

        @NotBlank(message = "Rack không được để trống")
        @Size(max = 10, message = "Rack không được vượt quá 10 ký tự")
        String rack,

        @NotNull(message = "Level không được để trống")
        Short level,

        @NotBlank(message = "Bin không được để trống")
        @Size(max = 10, message = "Bin không được vượt quá 10 ký tự")
        String bin,

        @Size(max = 20, message = "Loại vị trí không được vượt quá 20 ký tự")
        String locationType,

        BigDecimal maxWeightKg,
        BigDecimal maxVolumeCm3,
        Integer pickSequence,

        @Size(max = 20, message = "Trạng thái không được vượt quá 20 ký tự")
        String status,
        Boolean isActive
) {
}