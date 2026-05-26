package com.inbound_service.dto.response;

import java.util.UUID;

public record SupplierReturnProductOptionResponse(
        UUID productId,
        String sku,
        String name,
        UUID supplierId,
        String supplierName,
        Integer totalQtyAvailable,
        Integer locationCount
) {
}
