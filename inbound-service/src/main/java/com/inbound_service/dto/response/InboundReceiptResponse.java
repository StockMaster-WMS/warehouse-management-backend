package com.inbound_service.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InboundReceiptResponse(
        UUID id,
        String receiptNumber,
        UUID purchaseOrderId,
        String poNumber,
        UUID warehouseId,
        UUID locationId,
        String status,
        String note,
        LocalDate receivedDate,
        UUID receivedBy,
        OffsetDateTime createdAt,
        List<InboundReceiptItemResponse> items
) {
}
