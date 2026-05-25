package com.inbound_service.dto.request;

import java.util.UUID;

public record CompletePutawayRequest(
        UUID actualLocationId
) {
}
