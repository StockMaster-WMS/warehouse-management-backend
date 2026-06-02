package com.ai_service.context;

import com.ai_service.intent.AiIntent;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;

import java.util.ArrayList;
import java.util.List;

public record AiQualityAssessment(
        String intentQuality,
        boolean needsClarification,
        String clarificationReason,
        List<String> qualitySignals
) {
    private static final double HIGH_CONFIDENCE = 0.85;
    private static final double MEDIUM_CONFIDENCE = 0.65;

    public AiQualityAssessment {
        intentQuality = intentQuality == null ? "LOW" : intentQuality;
        clarificationReason = clarificationReason == null ? "" : clarificationReason;
        qualitySignals = qualitySignals == null ? List.of() : List.copyOf(qualitySignals);
    }

    public static AiQualityAssessment from(AiIntentResult route, AiToolResult toolResult,
            List<String> missingParams, int rowsReturned) {
        AiIntent intent = route == null || route.getIntent() == null ? AiIntent.UNSUPPORTED : route.getIntent();
        double confidence = route == null || route.getConfidence() == null ? 0.0 : route.getConfidence();
        List<String> safeMissing = missingParams == null ? List.of() : missingParams;

        String quality = qualityLabel(intent, confidence);
        String reason = clarificationReason(intent, confidence, safeMissing);
        boolean needsClarification = !reason.isBlank();

        List<String> signals = new ArrayList<>();
        signals.add("confidence:" + quality.toLowerCase());
        signals.add("dataBacked:" + (toolResult != null && toolResult.dataBacked()));
        signals.add("rowsReturned:" + Math.max(rowsReturned, 0));
        if (!safeMissing.isEmpty()) {
            signals.add("missingParams:" + safeMissing.size());
        }
        if (toolResult == null || toolResult.dataSources() == null || toolResult.dataSources().isEmpty()) {
            signals.add("dataSources:none");
        }
        if (toolResult != null && toolResult.toolName() != null
                && toolResult.toolName().toLowerCase().contains("forbidden")) {
            signals.add("authorization:blocked");
        }
        if (intent == AiIntent.AMBIGUOUS || intent == AiIntent.UNSUPPORTED) {
            signals.add("intent:" + intent.name().toLowerCase());
        }

        return new AiQualityAssessment(quality, needsClarification, reason, signals);
    }

    private static String qualityLabel(AiIntent intent, double confidence) {
        if (intent == AiIntent.UNSUPPORTED) {
            return "UNSUPPORTED";
        }
        if (intent == AiIntent.AMBIGUOUS || confidence < MEDIUM_CONFIDENCE) {
            return "LOW";
        }
        if (confidence < HIGH_CONFIDENCE) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private static String clarificationReason(AiIntent intent, double confidence, List<String> missingParams) {
        if (missingParams != null && !missingParams.isEmpty()) {
            return "missing_parameters";
        }
        if (intent == AiIntent.AMBIGUOUS) {
            return "ambiguous_intent";
        }
        if (intent == AiIntent.UNSUPPORTED) {
            return "unsupported_intent";
        }
        if (confidence < MEDIUM_CONFIDENCE) {
            return "low_confidence";
        }
        return "";
    }
}
