package com.ai_putway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ChatRequest {

    @NotBlank(message = "Câu hỏi không được để trống")
    private String question;

    private String role;
    private UUID userId;
    private List<UUID> warehouseIds;
}
