package com.inbound_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateInboundReceiptRequest(
        @NotNull(message = "Don nhap khong duoc de trong")
        UUID purchaseOrderId,

        UUID locationId,

        String note,

        @NotEmpty(message = "Can it nhat mot dong nhan hang")
        @Valid
        List<ReceiveLineRequest> items
) {
}
