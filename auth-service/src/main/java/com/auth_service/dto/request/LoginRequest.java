package com.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Ten dang nhap khong duoc de trong")
        String username,

        @NotBlank(message = "Mat khau khong duoc de trong")
        String password
) {
}
