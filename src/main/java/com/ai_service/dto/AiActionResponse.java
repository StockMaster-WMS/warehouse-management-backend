package com.ai_service.dto;

import java.util.List;
import java.util.Map;

public record AiActionResponse(
        String actionType,
        String status,
        String summary,
        boolean requiresConfirmation,
        String targetStatus,
        int candidateCount,
        int eligibleCount,
        int updatedCount,
        int skippedCount,
        List<AiActionCandidate> candidates,
        List<String> warnings,
        Map<String, Object> metadata
) {
    public AiActionResponse {
        candidates = candidates == null ? List.of() : candidates;
        warnings = warnings == null ? List.of() : warnings;
        metadata = metadata == null ? Map.of() : metadata;
    }

    public record AiActionCandidate(
            String sku,
            String productName,
            String currentStatus,
            String targetStatus,
            long qtyAvailable,
            Integer minStockQty,
            boolean eligible,
            String reason
    ) {
    }
}
