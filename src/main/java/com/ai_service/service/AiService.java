package com.ai_service.service;

import com.ai_service.client.AiModelSelectionContext;
import com.ai_service.context.AiQueryContext;
import com.ai_service.dto.AiAskRequest;
import com.ai_service.dto.AiAskResponse;
import com.ai_service.dto.AiAskResponse.AiActionSuggestion;
import com.ai_service.dto.AiAskResponse.AiResponseMetadata;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.service.conversation.AiAnswerComposerService;
import com.ai_service.service.conversation.AiIntentRouterService;
import com.ai_service.service.conversation.AiResponseEnrichmentService;
import com.ai_service.service.session.AiAuditService;
import com.ai_service.service.session.AiCancelService;
import com.ai_service.service.session.AiHistoryService;
import com.ai_service.service.tool.AiToolExecutorService;
import com.ai_service.tool.AiToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final int MAX_BATCH_QUESTIONS = 20;

    private final AiIntentRouterService intentRouterService;
    private final AiToolExecutorService toolExecutorService;
    private final AiAnswerComposerService answerComposerService;
    private final AiResponseEnrichmentService responseEnrichmentService;
    private final AiHistoryService historyService;
    private final AiAuditService auditService;
    private final AiCancelService cancelService;
    private final ObjectMapper objectMapper;

    // Xử lý câu hỏi AI dạng đồng bộ và trả về một response.
    public AiAskResponse ask(AiAskRequest req) {
        String sessionId = req.getSessionId();
        String userMessage = getUserMessage(req);
        long start = System.currentTimeMillis();

        String auditPayload = null;
        int rowsReturned = 0;
        String error = null;
        String finalReply = null;

        AiModelSelectionContext.set(req.getProvider(), req.getModel());
        try {
            log.info("AI ask start session={} provider={} model={} question='{}'",
                    sessionId, req.getProvider(), req.getModel(), preview(userMessage));
            List<Map<String, String>> history = historyService.getMessages(sessionId);
            List<String> batchQuestions = splitBatchQuestions(userMessage);
            if (batchQuestions.size() > 1) {
                List<SingleAiResult> results = processBatchQuestions(batchQuestions, history);
                rowsReturned = results.stream().mapToInt(SingleAiResult::rowsReturned).sum();
                auditPayload = toBatchAuditPayload(results);
                finalReply = formatBatchReply(results);
                log.info("AI batch sync session={} questions={} totalRows={} totalMs={}",
                        sessionId, results.size(), rowsReturned, System.currentTimeMillis() - start);
                return new AiAskResponse(finalReply, null, buildBatchMetadata(results, rowsReturned));
            }
            long routeStart = System.currentTimeMillis();
            AiIntentResult route = intentRouterService.route(userMessage, history);
            long toolStart = System.currentTimeMillis();
            AiToolResult toolResult = toolExecutorService.execute(route);
            long composeStart = System.currentTimeMillis();
            rowsReturned = toolExecutorService.estimateRows(toolResult);
            AiQueryContext queryContext = AiQueryContext.from(userMessage, route, toolResult, rowsReturned);
            auditPayload = toAuditPayload(queryContext);

            log.info("AI intent={} tool={} sources={} missingParams={} rows={}",
                    route.getIntent(), toolResult.toolName(), queryContext.dataSources(),
                    queryContext.missingParams(), rowsReturned);

            finalReply = answerComposerService.compose(userMessage, route, toolResult, history, queryContext);
            log.info("AI timings sync session={} intent={} routeMs={} toolMs={} composeMs={} totalMs={}",
                    sessionId, route.getIntent(), toolStart - routeStart, composeStart - toolStart,
                    System.currentTimeMillis() - composeStart, System.currentTimeMillis() - start);
            return new AiAskResponse(finalReply, null,
                    responseEnrichmentService.build(route, toolResult, queryContext));
        } catch (Exception e) {
            error = e.getMessage();
            log.error("AI Service error", e);
            finalReply = "Rất tiếc, hiện tại tôi chưa thể truy vấn dữ liệu này. Bạn vui lòng thử lại sau hoặc hỏi cụ thể hơn.";
            return new AiAskResponse(finalReply, error);
        } finally {
            auditService.log(sessionId, userMessage, auditPayload, rowsReturned, error,
                    System.currentTimeMillis() - start);
            if (finalReply != null) {
                historyService.addHistory(sessionId, userMessage, finalReply);
            }
            AiModelSelectionContext.clear();
        }
    }

    // Xử lý câu hỏi AI dạng stream và đẩy từng đoạn trả lời ra callback.
    public void askStream(AiAskRequest req, Consumer<String> fragmentConsumer) {
        askStream(req, fragmentConsumer, null);
    }

    // Xử lý câu hỏi AI dạng stream, kèm metadata để UI có thể hiển thị nguồn dữ liệu và gợi ý.
    public void askStream(AiAskRequest req, Consumer<String> fragmentConsumer,
            Consumer<AiResponseMetadata> metadataConsumer) {
        String sessionId = req.getSessionId();
        String requestId = StringUtils.hasText(req.getRequestId()) ? req.getRequestId() : sessionId;
        cancelService.startSession(requestId);
        String userMessage = getUserMessage(req);
        long start = System.currentTimeMillis();

        String auditPayload = null;
        int rowsReturned = 0;
        String error = null;
        StringBuilder finalReply = new StringBuilder();
        boolean cancelled = false;

        AiModelSelectionContext.set(req.getProvider(), req.getModel());
        try {
            log.info("AI stream start session={} request={} provider={} model={} question='{}'",
                    sessionId, requestId, req.getProvider(), req.getModel(), preview(userMessage));
            if (cancelService.isCancelled(requestId)) {
                log.info("AI stream cancelled before history session={} request={}", sessionId, requestId);
                return;
            }
            List<Map<String, String>> history = historyService.getMessages(sessionId);
            if (cancelService.isCancelled(requestId)) {
                log.info("AI stream cancelled before cache session={} request={}", sessionId, requestId);
                return;
            }
            List<String> batchQuestions = splitBatchQuestions(userMessage);
            if (batchQuestions.size() > 1) {
                List<SingleAiResult> results = new ArrayList<>();
                for (String question : batchQuestions) {
                    if (cancelService.isCancelled(requestId)) {
                        break;
                    }
                    SingleAiResult result = processSingleQuestion(question, history);
                    results.add(result);
                    rowsReturned += result.rowsReturned();
                    String fragment = formatBatchReplyItem(results.size(), result);
                    finalReply.append(fragment);
                    fragmentConsumer.accept(fragment);
                }
                if (!results.isEmpty()) {
                    auditPayload = toBatchAuditPayload(results);
                    if (metadataConsumer != null && !cancelService.isCancelled(requestId)) {
                        metadataConsumer.accept(buildBatchMetadata(results, rowsReturned));
                    }
                }
                cancelled = cancelService.isCancelled(requestId);
                log.info("AI batch stream session={} request={} questions={} totalRows={} cancelled={} totalMs={}",
                        sessionId, requestId, results.size(), rowsReturned, cancelled,
                        System.currentTimeMillis() - start);
                return;
            }
            long routeStart = System.currentTimeMillis();
            AiIntentResult route = intentRouterService.route(userMessage, history);
            if (cancelService.isCancelled(requestId)) {
                log.info("AI stream cancelled after route session={} request={}", sessionId, requestId);
                return;
            }
            long toolStart = System.currentTimeMillis();
            AiToolResult toolResult = toolExecutorService.execute(route);
            if (cancelService.isCancelled(requestId)) {
                log.info("AI stream cancelled after tool session={} request={}", sessionId, requestId);
                return;
            }
            long composeStart = System.currentTimeMillis();
            rowsReturned = toolExecutorService.estimateRows(toolResult);
            AiQueryContext queryContext = AiQueryContext.from(userMessage, route, toolResult, rowsReturned);
            auditPayload = toAuditPayload(queryContext);

            log.info("AI stream intent={} tool={} sources={} missingParams={} rows={}",
                    route.getIntent(), toolResult.toolName(), queryContext.dataSources(),
                    queryContext.missingParams(), rowsReturned);
            if (metadataConsumer != null && !cancelService.isCancelled(requestId)) {
                metadataConsumer.accept(responseEnrichmentService.build(route, toolResult, queryContext));
            }

            answerComposerService.composeStream(userMessage, route, toolResult, history, queryContext, fragment -> {
                if (cancelService.isCancelled(requestId)) {
                    return;
                }
                finalReply.append(fragment);
                fragmentConsumer.accept(fragment);
            }, () -> cancelService.isCancelled(requestId));
            cancelled = cancelService.isCancelled(requestId);
            log.info("AI timings stream session={} request={} intent={} routeMs={} toolMs={} composeMs={} totalMs={}",
                    sessionId, requestId, route.getIntent(), toolStart - routeStart, composeStart - toolStart,
                    System.currentTimeMillis() - composeStart, System.currentTimeMillis() - start);
        } catch (Exception e) {
            error = e.getMessage();
            log.error("AI stream error", e);
            if (!cancelService.isCancelled(requestId)) {
                String fallback = "Rất tiếc, hiện tại tôi chưa thể truy vấn dữ liệu này.";
                finalReply.append(fallback);
                fragmentConsumer.accept(fallback);
            }
        } finally {
            auditService.log(sessionId, userMessage, auditPayload, rowsReturned, error,
                    System.currentTimeMillis() - start);
            cancelled = cancelled || cancelService.isCancelled(requestId);
            if (!cancelled && !finalReply.isEmpty()) {
                historyService.addHistory(sessionId, userMessage, finalReply.toString());
            }
            cancelService.clear(requestId);
            AiModelSelectionContext.clear();
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.substring(0, Math.min(120, compact.length()));
    }

    private List<SingleAiResult> processBatchQuestions(List<String> questions, List<Map<String, String>> history) {
        List<SingleAiResult> results = new ArrayList<>();
        for (String question : questions) {
            results.add(processSingleQuestion(question, history));
        }
        return results;
    }

    private SingleAiResult processSingleQuestion(String question, List<Map<String, String>> history) {
        AiIntentResult route = intentRouterService.route(question, history);
        AiToolResult toolResult = toolExecutorService.execute(route);
        int rows = toolExecutorService.estimateRows(toolResult);
        AiQueryContext context = AiQueryContext.from(question, route, toolResult, rows);
        String reply = answerComposerService.compose(question, route, toolResult, history, context);
        return new SingleAiResult(question, route, toolResult, context, reply, rows);
    }

    private List<String> splitBatchQuestions(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return List.of();
        }
        String normalized = userMessage.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> candidates = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            String cleaned = cleanQuestionLine(line);
            if (StringUtils.hasText(cleaned)) {
                candidates.add(cleaned);
            }
        }
        if (candidates.size() <= 1 && normalized.indexOf('?') >= 0) {
            candidates.clear();
            for (String part : normalized.split("(?<=\\?)\\s+")) {
                String cleaned = cleanQuestionLine(part);
                if (StringUtils.hasText(cleaned)) {
                    candidates.add(cleaned);
                }
            }
        }
        if (candidates.size() <= 1) {
            return candidates;
        }
        return candidates.stream()
                .filter(question -> question.length() >= 4)
                .limit(MAX_BATCH_QUESTIONS)
                .toList();
    }

    private String cleanQuestionLine(String line) {
        if (line == null) {
            return "";
        }
        return line.trim()
                .replaceAll("^[\\-•*]+\\s*", "")
                .replaceAll("^\\d+[\\).:-]\\s*", "")
                .trim();
    }

    private String formatBatchReply(List<SingleAiResult> results) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            builder.append(formatBatchReplyItem(i + 1, results.get(i)));
        }
        return builder.toString().trim();
    }

    private String formatBatchReplyItem(int index, SingleAiResult result) {
        return index + ". " + result.question() + "\n"
                + result.reply().trim() + "\n\n";
    }

    private AiResponseMetadata buildBatchMetadata(List<SingleAiResult> results, int totalRows) {
        Set<String> dataSources = new LinkedHashSet<>();
        Set<String> missingParams = new LinkedHashSet<>();
        List<String> suggestedQuestions = new ArrayList<>();
        List<AiActionSuggestion> actions = new ArrayList<>();
        double confidenceSum = 0.0;
        int confidenceCount = 0;

        for (SingleAiResult result : results) {
            AiResponseMetadata metadata = responseEnrichmentService.build(
                    result.route(), result.toolResult(), result.context());
            dataSources.addAll(metadata.dataSources());
            missingParams.addAll(metadata.missingParams());
            addLimited(suggestedQuestions, metadata.suggestedQuestions(), 8);
            addLimited(actions, metadata.actions(), 5);
            if (metadata.confidence() != null) {
                confidenceSum += metadata.confidence();
                confidenceCount++;
            }
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("questionCount", results.size());
        parameters.put("questions", results.stream().map(SingleAiResult::question).toList());

        return new AiResponseMetadata(
                "MULTI_QUESTION",
                confidenceCount == 0 ? 0.0 : confidenceSum / confidenceCount,
                "batch",
                "BatchAiTool",
                List.copyOf(dataSources),
                List.copyOf(missingParams),
                totalRows,
                parameters,
                suggestedQuestions,
                actions,
                List.copyOf(missingParams).isEmpty() ? "MEDIUM" : "LOW",
                !List.copyOf(missingParams).isEmpty(),
                List.copyOf(missingParams).isEmpty() ? "" : "missing_parameters",
                List.of("questions:" + results.size(), "rowsReturned:" + totalRows)
        );
    }

    private <T> void addLimited(List<T> target, List<T> source, int limit) {
        if (source == null || target.size() >= limit) {
            return;
        }
        for (T item : source) {
            if (target.size() >= limit) {
                return;
            }
            if (!target.contains(item)) {
                target.add(item);
            }
        }
    }

    // Lấy nội dung người dùng từ message hoặc question.
    private String getUserMessage(AiAskRequest req) {
        String message = req.getMessage();
        if (StringUtils.hasText(message)) {
            return message.trim();
        }
        String question = req.getQuestion();
        if (StringUtils.hasText(question)) {
            return question.trim();
        }
        return "";
    }

    // Tạo payload JSON để ghi audit route và tool đã dùng.
    private String toAuditPayload(AiQueryContext context) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "intent", context == null ? "UNSUPPORTED" : context.intent().name(),
                    "parameters", context == null ? Map.of() : context.parameters(),
                    "tool", context == null ? "none" : context.toolName(),
                    "domain", context == null ? "general" : context.domain(),
                    "dataSources", context == null ? List.of() : context.dataSources(),
                    "missingParams", context == null ? List.of() : context.missingParams(),
                    "intentQuality", context == null ? "LOW" : context.quality().intentQuality(),
                    "needsClarification", context != null && context.quality().needsClarification(),
                    "rows", context == null ? 0 : context.rowCount()
            ));
        } catch (Exception e) {
            return context == null ? null : context.intent().name();
        }
    }

    private String toBatchAuditPayload(List<SingleAiResult> results) {
        try {
            List<Map<String, Object>> items = results.stream()
                    .map(result -> Map.<String, Object>of(
                            "question", result.question(),
                            "intent", result.context().intent().name(),
                            "parameters", result.context().parameters(),
                            "tool", result.context().toolName(),
                            "domain", result.context().domain(),
                            "dataSources", result.context().dataSources(),
                            "missingParams", result.context().missingParams(),
                            "intentQuality", result.context().quality().intentQuality(),
                            "needsClarification", result.context().quality().needsClarification(),
                            "rows", result.context().rowCount()
                    ))
                    .toList();
            return objectMapper.writeValueAsString(Map.of(
                    "intent", "MULTI_QUESTION",
                    "questionCount", results.size(),
                    "items", items
            ));
        } catch (Exception e) {
            return "MULTI_QUESTION";
        }
    }

    private record SingleAiResult(
            String question,
            AiIntentResult route,
            AiToolResult toolResult,
            AiQueryContext context,
            String reply,
            int rowsReturned
    ) {
    }
}
