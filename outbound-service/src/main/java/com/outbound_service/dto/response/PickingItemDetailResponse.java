package com.outbound_service.dto.response;

import java.util.UUID;

/**
 * Chi tiết picking item dành cho giao diện picker
 * Bao gồm đầy đủ thông tin SKU, vị trí, lô, tồn kho
 */
public record PickingItemDetailResponse(
        UUID id,
        UUID soItemId,
        String salesOrderNumber,
        
        // Product info
        UUID productId,
        String productCode,
        String productName,
        String productSku,
        String barcodeEan13,
        String categoryName,
        String baseUnit,
        
        // Location info
        UUID locationId,
        String locationCode,
        String locationName,
        String zone,
        String aisle,
        String shelf,
        String position,
        
        // Picking details
        String lotNumber,
        Integer qtyToPick,
        Integer qtyPicked,
        Integer qtyAvailable,
        
        // Status
        String status,
        Integer pickSequence
) {
}
