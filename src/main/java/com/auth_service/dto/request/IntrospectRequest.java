package com.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IntrospectRequest(
        @NotBlank(message = "Token không được để trống")
        String token
) {
}
