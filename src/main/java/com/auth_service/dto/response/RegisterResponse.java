package com.auth_service.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String username,
        String email,
        String roles,
        OffsetDateTime createdAt
) {
}
