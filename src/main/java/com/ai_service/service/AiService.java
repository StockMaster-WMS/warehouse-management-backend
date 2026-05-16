package com.ai_service.service;

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

        try {
            List<Map<String, String>> history = historyService.getMessages(sessionId);
            // quick-return if same question was asked before in this session
            String cached = historyService.findLastAnswerForQuestion(sessionId, userMessage);
            if (cached != null) {
                log.info("Returning cached answer for session={}, question={}", sessionId, userMessage);
                return new AiAskResponse(cached, null);
            }
            AiIntentResult route = intentRouterService.route(userMessage, history);
            AiToolResult toolResult = toolExecutorService.execute(route);
            rowsReturned = toolExecutorService.estimateRows(toolResult);
            auditPayload = toAuditPayload(route, toolResult);

            log.info("AI intent={}, tool={}, rows={}",
                    route.getIntent(), toolResult.toolName(), rowsReturned);

            finalReply = answerComposerService.compose(userMessage, route, toolResult, history);
            return new AiAskResponse(finalReply, null);
        } catch (Exception e) {
            error = e.getMessage();
            log.error("AI Service error", e);
            finalReply = "Rất tiếc, hiện tại tôi chưa thể truy vấn dữ liệu này. Bạn vui lòng thử lại sau hoặc hỏi cụ thể hơn.";
            return new AiAskResponse(finalReply, error);
        } finally {
            auditService.log(sessionId, userMessage, auditPayload, rowsReturned, error,
                    System.currentTimeMillis() - start);
            historyService.addHistory(sessionId, userMessage, finalReply);
        }
    }

    // Xử lý câu hỏi AI dạng stream và đẩy từng đoạn trả lời ra callback.
    public void askStream(AiAskRequest req, Consumer<String> fragmentConsumer) {
        String sessionId = req.getSessionId();
        // register session for cancellation
        // lazy-get AiCancelService via context to avoid constructor change in many places
        var cancelService = com.ai_service.util.ApplicationContextHolder.getBean(com.ai_service.service.AiCancelService.class);
        cancelService.startSession(sessionId);
        String userMessage = getUserMessage(req);
        long start = System.currentTimeMillis();

        String auditPayload = null;
        int rowsReturned = 0;
        String error = null;
        StringBuilder finalReply = new StringBuilder();

        try {
            List<Map<String, String>> history = historyService.getMessages(sessionId);
            // quick-return if same question was asked before in this session
            String cached = historyService.findLastAnswerForQuestion(sessionId, userMessage);
            if (cached != null) {
                log.info("Returning cached answer for session={}, question={}", sessionId, userMessage);
                fragmentConsumer.accept(cached);
                return;
            }
            AiIntentResult route = intentRouterService.route(userMessage, history);
            AiToolResult toolResult = toolExecutorService.execute(route);
            rowsReturned = toolExecutorService.estimateRows(toolResult);
            auditPayload = toAuditPayload(route, toolResult);

            log.info("AI stream intent={}, tool={}, rows={}",
                    route.getIntent(), toolResult.toolName(), rowsReturned);

            answerComposerService.composeStream(userMessage, route, toolResult, history, fragment -> {
                finalReply.append(fragment);
                fragmentConsumer.accept(fragment);
            }, () -> cancelService.isCancelled(sessionId));
            // if cancelled, send a notice fragment
            if (cancelService.isCancelled(sessionId)) {
                fragmentConsumer.accept("\n--- (Đã huỷ) ---");
            }
        } catch (Exception e) {
            error = e.getMessage();
            log.error("AI stream error", e);
            String fallback = "Rất tiếc, hiện tại tôi chưa thể truy vấn dữ liệu này.";
            finalReply.append(fallback);
            fragmentConsumer.accept(fallback);
        } finally {
            auditService.log(sessionId, userMessage, auditPayload, rowsReturned, error,
                    System.currentTimeMillis() - start);
            historyService.addHistory(sessionId, userMessage, finalReply.toString());
            // clear cancel token
            var cancelService2 = com.ai_service.util.ApplicationContextHolder.getBean(com.ai_service.service.AiCancelService.class);
            cancelService2.clear(sessionId);
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
