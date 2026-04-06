package com.inbound_service.client;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSummaryData(
        UUID id,
        String sku,
        String name,
        Integer minQty
) {
}