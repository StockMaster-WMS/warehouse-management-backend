package com.ai_service.service.tool;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentCatalog;
import com.ai_service.intent.AiIntentResult;

import java.util.List;
import java.util.Map;

record AiToolExecutionPlan(
        AiIntent intent,
        Map<String, Object> parameters,
        String domain,
        String expectedToolName,
        List<String> dataSources,
        List<String> missingParams,
        boolean allowed
) {
    static AiToolExecutionPlan from(AiIntentResult route, boolean allowed) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        Map<String, Object> parameters = route == null ? Map.of() : route.safeParameters();
        var definition = AiIntentCatalog.get(intent);
        return new AiToolExecutionPlan(
                intent,
                parameters,
                definition.domain(),
                definition.toolName(),
                definition.dataSources(),
                definition.missingParams(parameters),
                allowed
        );
    }
}
