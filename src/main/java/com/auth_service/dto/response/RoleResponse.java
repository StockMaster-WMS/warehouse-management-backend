package com.auth_service.dto.response;

import java.util.UUID;

public record RoleResponse(
        UUID id,
        String code,
        String name,
        String description
) {
}
