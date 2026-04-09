package com.inbound_service.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InboundPrintResponse(
        UUID id,
        String receiptNumber,
        String poNumber,
        UUID warehouseId,
        UUID locationId,
        LocalDate receivedDate,
        String supplierName,
        String supplierAddress,
        String supplierPhone,
        UUID receivedBy,
        String note,
        List<InboundPrintItemResponse> items
) {
}
