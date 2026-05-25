package com.inbound_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceiveLineRequest(
        @NotNull(message = "Dong PO khong duoc de trong")
        UUID poItemId,

        @NotNull(message = "So luong nhan khong duoc de trong")
        @Min(value = 1, message = "So luong nhan phai lon hon 0")
        Integer receivedQty,

        UUID locationId,

        String note
) {
}
