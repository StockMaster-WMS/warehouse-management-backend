package com.inbound_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateInboundReceiptRequest(
        @NotNull(message = "Đơn nhập không được để trống")
        UUID purchaseOrderId,

        @NotNull(message = "Vị trí nhận hàng không được để trống")
        UUID locationId,

        String note,

        @NotEmpty(message = "Cần ít nhất một dòng nhận hàng")
        @Valid
        List<ReceiveLineRequest> items
) {
}
