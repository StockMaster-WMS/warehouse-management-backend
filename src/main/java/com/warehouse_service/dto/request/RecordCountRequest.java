package com.warehouse_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RecordCountRequest(
    List<ItemResult> results
) {
    public record ItemResult(
        UUID productId,
        UUID locationId,
        Integer actualQty,
        String notes
    ) {}
}

