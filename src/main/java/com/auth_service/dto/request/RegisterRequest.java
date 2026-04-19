package com.auth_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 4, max = 50, message = "Tên đăng nhập từ 4 đến 50 ký tự")
        @Schema(example = "newuser", description = "Tên đăng nhập mới")
        String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 120, message = "Email không được vượt quá 120 ký tự")
        @Schema(example = "user@example.com", description = "Địa chỉ email")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu từ 8 đến 100 ký tự")
        @Schema(example = "Password@123", description = "Mật khẩu bảo mật")
        String password
) {
}
