package com.ai_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class AiAskResponse {
    private String reply;
    private String error;
    private String intent;
    private Double confidence;
    private String domain;
    private String toolName;
    private List<String> dataSources = List.of();
    private List<String> missingParams = List.of();
    private Integer rowsReturned;
    private Map<String, Object> parameters = Map.of();
    private List<String> suggestedQuestions = List.of();
    private List<AiActionSuggestion> actions = List.of();
    private String intentQuality;
    private Boolean needsClarification;
    private String clarificationReason;
    private List<String> qualitySignals = List.of();
    private Map<String, Object> display = Map.of();
    private List<Map<String, Object>> resultRows = List.of();
    private List<Map<String, Object>> candidateSuggestions = List.of();

    public AiAskResponse(String reply, String error) {
        this.reply = reply;
        this.error = error;
    }

    public AiAskResponse(String reply, String error, AiResponseMetadata metadata) {
        this.reply = reply;
        this.error = error;
        if (metadata != null) {
            this.intent = metadata.intent();
            this.confidence = metadata.confidence();
            this.domain = metadata.domain();
            this.toolName = metadata.toolName();
            this.dataSources = metadata.dataSources();
            this.missingParams = metadata.missingParams();
            this.rowsReturned = metadata.rowsReturned();
            this.parameters = metadata.parameters();
            this.suggestedQuestions = metadata.suggestedQuestions();
            this.actions = metadata.actions();
            this.intentQuality = metadata.intentQuality();
            this.needsClarification = metadata.needsClarification();
            this.clarificationReason = metadata.clarificationReason();
            this.qualitySignals = metadata.qualitySignals();
            this.display = metadata.display();
            this.resultRows = metadata.resultRows();
            this.candidateSuggestions = metadata.candidateSuggestions();
        }
    }

    public record AiResponseMetadata(
            String intent,
            Double confidence,
            String domain,
            String toolName,
            List<String> dataSources,
            List<String> missingParams,
            Integer rowsReturned,
            Map<String, Object> parameters,
            List<String> suggestedQuestions,
            List<AiActionSuggestion> actions,
            String intentQuality,
            Boolean needsClarification,
            String clarificationReason,
            List<String> qualitySignals,
            Map<String, Object> display,
            List<Map<String, Object>> resultRows,
            List<Map<String, Object>> candidateSuggestions
    ) {
        public AiResponseMetadata {
            domain = domain == null ? "general" : domain;
            dataSources = dataSources == null ? List.of() : dataSources;
            missingParams = missingParams == null ? List.of() : missingParams;
            parameters = parameters == null ? Map.of() : parameters;
            suggestedQuestions = suggestedQuestions == null ? List.of() : suggestedQuestions;
            actions = actions == null ? List.of() : actions;
            intentQuality = intentQuality == null ? "LOW" : intentQuality;
            needsClarification = needsClarification != null && needsClarification;
            clarificationReason = clarificationReason == null ? "" : clarificationReason;
            qualitySignals = qualitySignals == null ? List.of() : qualitySignals;
            display = display == null ? Map.of() : display;
            resultRows = resultRows == null ? List.of() : resultRows;
            candidateSuggestions = candidateSuggestions == null ? List.of() : candidateSuggestions;
        }
    }

    public record AiActionSuggestion(
            String type,
            String label,
            String description,
            boolean requiresConfirmation,
            String requiresAuthority,
            Map<String, Object> payload
    ) {
        public AiActionSuggestion {
            payload = payload == null ? Map.of() : payload;
        }
    }
}
