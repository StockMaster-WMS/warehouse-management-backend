package com.ai_putway.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class LocationSuggestionRequest {

    @NotNull(message = "productId không được để trống")
    private UUID productId;

    @NotNull(message = "quantity không được để trống")
    @Positive(message = "quantity phải là số dương")
    private Integer quantity;

    @NotNull(message = "warehouseId không được để trống")
    private UUID warehouseId;

    private String currentAisle;
}
