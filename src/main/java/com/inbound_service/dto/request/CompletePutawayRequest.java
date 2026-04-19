package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CompletePutawayRequest(
        @NotNull UUID actualLocationId
) {
}
