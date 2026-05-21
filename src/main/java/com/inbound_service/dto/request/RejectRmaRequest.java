package com.inbound_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectRmaRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason
) {
}
