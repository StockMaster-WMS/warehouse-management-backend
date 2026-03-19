package com.auth_service.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresInSeconds,
        String username,
        String roles
) {
}
