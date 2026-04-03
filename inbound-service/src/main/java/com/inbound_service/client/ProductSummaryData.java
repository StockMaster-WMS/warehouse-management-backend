package com.inbound_service.client;

import java.util.UUID;

public record ProductSummaryData(
        UUID id,
        String sku,
        String name,
        Integer minQty
) {
}