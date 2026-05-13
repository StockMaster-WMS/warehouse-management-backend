package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReceiveRmaRequest(
    @NotNull UUID itemId,
    @NotNull Integer receivedQty,
    String condition,
    String notes,
    UUID locationId // Location to put back into
) {}
