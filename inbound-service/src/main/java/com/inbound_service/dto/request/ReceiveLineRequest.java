package com.inbound_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceiveLineRequest(
        @NotNull(message = "Dòng PO không được để trống")
        UUID poItemId,

        @NotNull(message = "Số lượng nhận không được để trống")
        @Min(value = 1, message = "Số lượng nhận phải lớn hơn 0")
        Integer receivedQty,

        String note
) {
}
