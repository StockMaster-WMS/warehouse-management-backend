package com.auth_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String fullName,
    String roles,
    Boolean isActive,
    OffsetDateTime createdAt,
    List<UUID> warehouseIds,
    List<String> warehouseNames
) {}
