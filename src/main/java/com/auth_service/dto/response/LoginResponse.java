package com.auth_service.dto.response;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String username,
            String email,
            String fullName,
            String roles
    ) {
    }
}
