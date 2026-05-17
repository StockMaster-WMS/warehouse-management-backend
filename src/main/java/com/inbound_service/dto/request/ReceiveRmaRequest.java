package com.inbound_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReceiveRmaRequest(
    @NotNull UUID itemId,
    @Min(value = 0, message = "Số lượng nhận không được âm")
    @NotNull Integer receivedQty,
    String condition,
    String notes,
    UUID locationId // Location to put back into
) {}
