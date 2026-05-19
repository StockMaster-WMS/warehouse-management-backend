package com.ai_service.service;

import com.ai_service.client.AiModelSelectionContext;
import com.ai_service.dto.AiAskRequest;
import com.ai_service.dto.AiAskResponse;
import com.ai_service.intent.AiIntentResult;
import com.ai_service.tool.AiToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiIntentRouterService intentRouterService;
    private final AiToolExecutorService toolExecutorService;
    private final AiAnswerComposerService answerComposerService;
    private final AiHistoryService historyService;
    private final AiAuditService auditService;
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
            log.info("AI ask start session={} question='{}'", sessionId, preview(userMessage));
            List<Map<String, String>> history = historyService.getMessages(sessionId);
            long routeStart = System.currentTimeMillis();
            AiIntentResult route = intentRouterService.route(userMessage, history);
            long toolStart = System.currentTimeMillis();
            AiToolResult toolResult = toolExecutorService.execute(route);
            long composeStart = System.currentTimeMillis();
            rowsReturned = toolExecutorService.estimateRows(toolResult);
            auditPayload = toAuditPayload(route, toolResult);

            log.info("AI intent={}, tool={}, rows={}",
                    route.getIntent(), toolResult.toolName(), rowsReturned);

            finalReply = answerComposerService.compose(userMessage, route, toolResult, history);
            log.info("AI timings sync session={} intent={} routeMs={} toolMs={} composeMs={} totalMs={}",
                    sessionId, route.getIntent(), toolStart - routeStart, composeStart - toolStart,
                    System.currentTimeMillis() - composeStart, System.currentTimeMillis() - start);
            return new AiAskResponse(finalReply, null);
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
        String sessionId = req.getSessionId();
        String requestId = StringUtils.hasText(req.getRequestId()) ? req.getRequestId() : sessionId;
        // register session for cancellation
        // lazy-get AiCancelService via context to avoid constructor change in many places
        var cancelService = com.ai_service.util.ApplicationContextHolder.getBean(com.ai_service.service.AiCancelService.class);
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
            log.info("AI stream start session={} request={} question='{}'",
                    sessionId, requestId, preview(userMessage));
            if (cancelService.isCancelled(requestId)) {
                log.info("AI stream cancelled before history session={} request={}", sessionId, requestId);
                return;
            }
            List<Map<String, String>> history = historyService.getMessages(sessionId);
            if (cancelService.isCancelled(requestId)) {
                log.info("AI stream cancelled before cache session={} request={}", sessionId, requestId);
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
            auditPayload = toAuditPayload(route, toolResult);

            log.info("AI stream intent={}, tool={}, rows={}",
                    route.getIntent(), toolResult.toolName(), rowsReturned);

            answerComposerService.composeStream(userMessage, route, toolResult, history, fragment -> {
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
            // clear cancel token
            var cancelService2 = com.ai_service.util.ApplicationContextHolder.getBean(com.ai_service.service.AiCancelService.class);
            cancelService2.clear(requestId);
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
    private String toAuditPayload(AiIntentResult route, AiToolResult toolResult) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "intent", route == null ? "UNSUPPORTED" : route.getIntent().name(),
                    "parameters", route == null ? Map.of() : route.safeParameters(),
                    "tool", toolResult == null ? "none" : toolResult.toolName()
            ));
        } catch (Exception e) {
            return route == null ? null : route.getIntent().name();
        }
    }
}
