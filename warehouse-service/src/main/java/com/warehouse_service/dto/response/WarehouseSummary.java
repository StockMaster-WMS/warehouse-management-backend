package com.warehouse_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarehouseSummary(
        UUID id,
        String code,
        String name
) {
}

