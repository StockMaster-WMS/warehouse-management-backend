package com.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken khong duoc de trong")
        String refreshToken
) {
}
