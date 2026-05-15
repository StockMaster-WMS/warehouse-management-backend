package com.ai_service.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiIntentResult {
    private AiIntent intent = AiIntent.UNSUPPORTED;
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private Double confidence = 0.0;
    private String reason;

    // Trả về parameters rỗng nếu model không cung cấp.
    public Map<String, Object> safeParameters() {
        return parameters == null ? Map.of() : parameters;
    }

    // Tạo kết quả intent với map parameters an toàn.
    public static AiIntentResult of(AiIntent intent, Map<String, Object> parameters, double confidence, String reason) {
        return new AiIntentResult(intent, parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters),
                confidence, reason);
    }
}
