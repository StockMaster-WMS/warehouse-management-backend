package com.ai_service.context;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentCatalog;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;

import java.util.List;
import java.util.Map;

public record AiQueryContext(
        String question,
        AiIntent intent,
        Map<String, Object> parameters,
        String toolName,
        List<String> dataSources,
        List<String> missingParams,
        int rowCount
) {
    public static AiQueryContext from(String question, AiIntentResult route, AiToolResult toolResult, int rowCount) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        Map<String, Object> parameters = route == null ? Map.of() : route.safeParameters();
        var definition = AiIntentCatalog.get(intent);
        List<String> sources = toolResult != null && toolResult.dataSources() != null && !toolResult.dataSources().isEmpty()
                ? toolResult.dataSources()
                : definition.dataSources();
        List<String> missing = toolResult != null && toolResult.missingParams() != null && !toolResult.missingParams().isEmpty()
                ? toolResult.missingParams()
                : definition.missingParams(parameters);
        return new AiQueryContext(
                question,
                intent,
                parameters,
                toolResult == null ? definition.toolName() : toolResult.toolName(),
                sources,
                missing,
                rowCount
        );
    }
}
