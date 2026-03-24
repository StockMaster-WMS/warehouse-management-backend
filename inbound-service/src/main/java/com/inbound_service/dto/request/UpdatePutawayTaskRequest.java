package com.inbound_service.dto.request;

import java.util.UUID;

public record UpdatePutawayTaskRequest(
        UUID suggestedLocationId,
        UUID assignedTo,
        String status
) {
}
