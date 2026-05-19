package com.ai_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAiProviderKeyRequest(
        @NotBlank(message = "API key không được để trống")
        @Size(max = 4096, message = "API key quá dài")
        String apiKey) {
}
