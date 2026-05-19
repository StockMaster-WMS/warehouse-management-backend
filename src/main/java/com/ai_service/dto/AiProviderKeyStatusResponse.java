package com.ai_service.dto;

import java.time.OffsetDateTime;

public record AiProviderKeyStatusResponse(
        String provider,
        String label,
        boolean configured,
        String keyPreview,
        OffsetDateTime updatedAt) {
}
