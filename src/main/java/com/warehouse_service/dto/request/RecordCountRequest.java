package com.warehouse_service.dto.request;

import java.util.List;
import java.util.UUID;

public record RecordCountRequest(
    List<ItemResult> results
) {
    public record ItemResult(
        UUID itemId,
        UUID productId,
        UUID locationId,
        String lotNumber,
        Integer actualQty,
        String notes
    ) {}
}

