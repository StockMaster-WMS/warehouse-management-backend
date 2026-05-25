package com.inbound_service.dto.response;

import java.util.UUID;

public record InboundLocationSuggestionResponse(
        UUID locationId,
        String locationCode,
        String locationType,
        String zone,
        boolean existingProductLocation,
        boolean emptyLocation,
        Integer qtyOnHand
) {
}
