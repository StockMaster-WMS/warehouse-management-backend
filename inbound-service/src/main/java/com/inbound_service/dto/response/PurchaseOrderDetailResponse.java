package com.inbound_service.dto.response;

import java.util.List;

public record PurchaseOrderDetailResponse(
        PurchaseOrderResponse purchaseOrder,
        List<PoItemResponse> items,
        List<PutawayTaskResponse> putawayTasks,
        List<InboundReceiptResponse> receipts,
        Integer totalOrderedQty,
        Integer totalReceivedQty,
        boolean fullyReceived
) {
}
