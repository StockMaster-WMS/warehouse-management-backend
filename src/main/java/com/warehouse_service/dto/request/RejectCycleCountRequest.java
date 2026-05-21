package com.warehouse_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectCycleCountRequest(
        @NotBlank(message = "Lý do yêu cầu kiểm lại không được để trống")
        String reason
) {
}
