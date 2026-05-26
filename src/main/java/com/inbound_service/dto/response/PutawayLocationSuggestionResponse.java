package com.inbound_service.dto.response;

import java.util.UUID;

public record PutawayLocationSuggestionResponse(
        UUID locationId,
        String locationCode,
        String locationType,
        String zone,
        boolean currentSuggested,
        boolean existingProductLocation,
        boolean emptyLocation,
        Integer qtyOnHand
) {
}
