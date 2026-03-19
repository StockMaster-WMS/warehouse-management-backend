package com.auth_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record IntrospectResponse(
        boolean active,
        UUID userId,
        String username,
        String roles,
        Instant expiresAt
) {
}
