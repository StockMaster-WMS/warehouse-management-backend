package com.outbound_service.dto.response;

import java.util.UUID;

public record PickingItemResponse(
        UUID id,
        UUID soItemId,
        UUID productId,
        UUID locationId,
        Integer qtyToPick,
        Integer qtyPicked,
        String status,
        Integer pickSequence
) {
}