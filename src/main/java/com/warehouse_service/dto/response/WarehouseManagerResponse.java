package com.warehouse_service.dto.response;

import java.util.UUID;

public record WarehouseManagerResponse(
        UUID id,
        String username,
        String email,
        String fullName
) {
}
