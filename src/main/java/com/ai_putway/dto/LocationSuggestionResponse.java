package com.ai_putway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LocationSuggestionResponse {
    private boolean success;
    private String error;

    @JsonProperty("product_id")
    private UUID productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("product_code")
    private String productCode;

    private Integer quantity;

    @JsonProperty("warehouse_id")
    private UUID warehouseId;

    @JsonProperty("total_available_locations")
    private Integer totalAvailableLocations;

    @JsonProperty("top_suggestions")
    private List<LocationSuggestion> topSuggestions;

    @Data
    public static class LocationSuggestion {
        @JsonProperty("location_id")
        private UUID locationId;

        @JsonProperty("location_code")
        private String locationCode;

        private String zone;
        private String aisle;
        private String rack;
        private String shelf;
        private String bin;
        private Integer capacity;

        @JsonProperty("current_load")
        private Integer currentLoad;

        @JsonProperty("available_space")
        private Integer availableSpace;

        private Double score;

        @JsonProperty("score_percent")
        private Double scorePercent;

        private List<String> reasons;

        @JsonProperty("color_code")
        private String colorCode;
    }
}
