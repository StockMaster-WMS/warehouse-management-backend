package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DispositionRmaItemRequest(
        @NotBlank(message = "Hành động xử lý là bắt buộc")
        @Size(max = 40)
        String action,

        UUID targetLocationId,

        UUID supplierId,

        @Size(max = 500)
        String note
) {
}
