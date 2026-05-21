package com.warehouse_service.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CycleCountResponse(
    UUID id,
    String countNumber,
    UUID warehouseId,
    String warehouseName,
    String status,
    String description,
    String scope,
    String scopeValue,
    OffsetDateTime scheduledAt,
    OffsetDateTime startedAt,
    OffsetDateTime submittedAt,
    OffsetDateTime completedAt,
    UUID assignedTo,
    UUID createdBy,
    UUID approvedBy,
    UUID rejectedBy,
    OffsetDateTime rejectedAt,
    String rejectionReason,
    OffsetDateTime createdAt,
    Integer totalLines,
    Integer countedLines,
    Integer discrepancyLines,
    Integer totalAbsDiscrepancy,
    List<LineResponse> lines
) {
    public record LineResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSku,
        UUID locationId,
        String locationCode,
        String lotNumber,
        Integer systemQty,
        Integer countedQty,
        Integer discrepancy,
        String varianceSeverity,
        String status,
        String notes
    ) {}
}
