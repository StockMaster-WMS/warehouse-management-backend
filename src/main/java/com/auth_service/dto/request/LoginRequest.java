package com.auth_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Schema(example = "admin", description = "Tên đăng nhập của người dùng")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Schema(example = "Admin@12345", description = "Mật khẩu của người dùng")
        String password
) {
}
