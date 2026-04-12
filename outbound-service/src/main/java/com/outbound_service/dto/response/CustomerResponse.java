package com.outbound_service.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String code,
        String name,
        String contactName,
        String phone,
        String email,
        String taxCode,
        Map<String, Object> address,
        String notes,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
