package com.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAskRequest {
    private String message;          
    private String question;
    private String sessionId;        
    private List<ChatMessage> history;
}
