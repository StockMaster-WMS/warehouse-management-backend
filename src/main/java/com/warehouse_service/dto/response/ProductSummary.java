package com.warehouse_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummary(
        UUID id,
        String sku,
        String name,
        Integer minQty
) {
}

