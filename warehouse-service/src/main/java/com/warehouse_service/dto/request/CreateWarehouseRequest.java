package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank(message = "Mã kho không được để trống")
        @Size(max = 20, message = "Mã kho không được vượt quá 20 ký tự")
        String code,

        @NotBlank(message = "Tên kho không được để trống")
        @Size(max = 150, message = "Tên kho không được vượt quá 150 ký tự")
        String name,

        String address,
        @Size(max = 120, message = "Tên người quản lý không được vượt quá 120 ký tự")
        String managerName,
        String timezone,
        Boolean isActive
) {
}
