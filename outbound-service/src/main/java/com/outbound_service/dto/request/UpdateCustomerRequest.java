package com.outbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateCustomerRequest(
        @NotBlank(message = "Mã khách hàng không được để trống")
        @Size(max = 30, message = "Mã khách hàng không được vượt quá 30 ký tự")
        String code,

        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(max = 200, message = "Tên khách hàng không được vượt quá 200 ký tự")
        String name,

        @Size(max = 120, message = "Tên người liên hệ không được vượt quá 120 ký tự")
        String contactName,

        @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
        String phone,

        @Size(max = 120, message = "Email không được vượt quá 120 ký tự")
        String email,

        @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
        String taxCode,

        Map<String, Object> address,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
        String notes,

        Boolean isActive
) {
}
