package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateRmaRequest(
    UUID salesOrderId,
    String customerName,
    @NotNull UUID warehouseId,
    String reason,
    @NotEmpty List<ItemRequest> items
) {
    public record ItemRequest(
        @NotNull UUID productId,
        @NotNull Integer expectedQty,
        String lotNumber
    ) {}
}
