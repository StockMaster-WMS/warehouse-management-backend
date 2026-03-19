package com.auth_service.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long accessTokenExpiresInSeconds,
        Long refreshTokenExpiresInSeconds,
        String username,
        String roles
) {
}
