package com.ai_service.controller;

import com.ai_service.dto.AiAskRequest;
import com.ai_service.dto.AiAskResponse;
import com.ai_service.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Service", description = "Các API chat với trợ lý AI StockMaster")
@PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'REPORT_VIEWER')")
public class AiController {

    private final AiService aiService;
    private final Executor aiTaskExecutor;
    private final com.ai_service.service.AiCancelService aiCancelService;
    private final ObjectMapper objectMapper;

    // Xử lý câu hỏi AI dạng trả lời một lần.
    @PostMapping("/ask")
    @Operation(summary = "Chat với trợ lý AI", description = "Gửi câu hỏi tới provider/model AI đang chọn")
    public ResponseEntity<AiAskResponse> ask(@RequestBody AiAskRequest req) {
        try {
            AiAskResponse response = aiService.ask(req);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI ASK Error: ", e);
            return ResponseEntity.status(500)
                    .body(new AiAskResponse(null, "Rất tiếc, hiện tại tôi chưa thể trả lời yêu cầu này."));
        }
    }

    @PostMapping(value = "/ask/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Hỏi đáp dữ liệu dạng Stream (SSE)", description = "Trả về câu trả lời theo thời gian thực")
    public SseEmitter askStream(@RequestBody AiAskRequest request) {
        return openStream(request);
    }

    private SseEmitter openStream(AiAskRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 phút timeout
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);
        String sessionId = request == null ? null : request.getSessionId();
        String requestId = request == null ? null : request.getRequestId();
        String cancelKey = StringUtils.hasText(requestId) ? requestId : sessionId;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        emitter.onTimeout(() -> {
            clientDisconnected.set(true);
            aiCancelService.cancel(cancelKey);
            emitter.complete();
        });
        emitter.onError(error -> {
            clientDisconnected.set(true);
            aiCancelService.cancel(cancelKey);
        });
        emitter.onCompletion(() -> {
            clientDisconnected.set(true);
            aiCancelService.cancel(cancelKey);
        });
        
        aiTaskExecutor.execute(() -> {
            var securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            try {
                sendModelSelection(emitter, request, clientDisconnected);
                aiService.askStream(request, fragment -> {
                    if (clientDisconnected.get()) {
                        return;
                    }
                    try {
                        emitter.send(fragment);
                    } catch (IOException e) {
                        clientDisconnected.set(true);
                        log.debug("SSE client disconnected: {}", e.getMessage());
                    }
                });
                if (!clientDisconnected.get()) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("Streaming Error: ", e);
                if (!clientDisconnected.get()) {
                    try {
                        emitter.send("Rất tiếc, hiện tại tôi chưa thể trả lời yêu cầu này.");
                    } catch (IOException sendError) {
                        clientDisconnected.set(true);
                        log.debug("Cannot send SSE fallback: {}", sendError.getMessage());
                    }
                }
                if (!clientDisconnected.get()) {
                    emitter.complete();
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    private void sendModelSelection(SseEmitter emitter, AiAskRequest request, AtomicBoolean clientDisconnected) {
        if (clientDisconnected.get()) {
            return;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("provider", cleanSelection(request == null ? null : request.getProvider()));
        payload.put("model", cleanSelection(request == null ? null : request.getModel()));
        try {
            emitter.send(SseEmitter.event()
                    .name("model")
                    .data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            clientDisconnected.set(true);
            log.debug("Cannot send AI model selection event: {}", e.getMessage());
        }
    }

    private String cleanSelection(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    @PostMapping("/cancel")
    @Operation(summary = "Huỷ phiên AI đang chạy", description = "Huỷ một phiên streaming AI theo sessionId")
    public ResponseEntity<?> cancel(@RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String requestId) {
        try {
            aiCancelService.cancel(StringUtils.hasText(requestId) ? requestId : sessionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Cancel error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Không thể hủy phiên AI lúc này.");
        }
    }
}
