package com.warehouse_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkLocationGeneratorRequest {

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    private String warehouseCodePrefix;

    private String areaCode;

    @NotBlank(message = "Zone is required")
    private String zone;

    // Counts for generation
    private String aislePrefix;
    @Builder.Default
    @Min(1)
    private Integer aisleStart = 1;
    @Min(1) @NotNull
    private Integer aisleCount;

    private String rackPrefix;
    @Builder.Default
    @Min(1)
    private Integer rackStart = 1;
    @Min(1) @NotNull
    private Integer rackCount;

    @Builder.Default
    @Min(1)
    private Integer levelStart = 1;
    @Min(1) @NotNull
    private Integer levelCount;

    private String binPrefix;
    @Builder.Default
    @Min(1)
    private Integer binStart = 1;
    @Min(1) @NotNull
    private Integer binCount;

    @Builder.Default
    private String locationType = "STORAGE";
}
