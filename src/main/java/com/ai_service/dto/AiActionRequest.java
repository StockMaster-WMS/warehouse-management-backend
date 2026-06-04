package com.ai_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class AiActionRequest {
    private String actionType;
    private String source;
    private List<String> skuList = List.of();
    private String targetStatus;
    private String reason;
    private Integer limit;
    private Map<String, Object> metadata = Map.of();
}
