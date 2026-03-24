package com.inbound_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceivePoItemRequest(
        @NotNull @Min(1) @Max(1_000_000) Integer qty,
        UUID suggestedLocationId
) {
}
