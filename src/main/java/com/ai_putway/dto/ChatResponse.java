package com.ai_putway.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private boolean success;
    private String error;
    private String question;
    private String answer;
    private String intent;
    private Double confidence;
}
