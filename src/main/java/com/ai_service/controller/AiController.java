package com.ai_service.controller;

import com.ai_service.dto.AiAskRequest;
import com.ai_service.dto.AiAskResponse;
import com.ai_service.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Service", description = "Các API chat với trợ lý AI StockMaster")
public class AiController {

    private final AiService aiService;
    private final Executor aiTaskExecutor;

    @PostMapping("/ask")
    @Operation(summary = "Chat với trợ lý AI", description = "Gửi câu hỏi tới model stockmaster-ai")
    public ResponseEntity<AiAskResponse> ask(@RequestBody AiAskRequest req) {
        try {
            AiAskResponse response = aiService.ask(req);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI ASK Error: ", e);
            return ResponseEntity.status(500).body(new AiAskResponse(null, e.getMessage()));
        }
    }

    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Hỏi đáp dữ liệu dạng Stream (SSE)", description = "Trả về câu trả lời theo thời gian thực (từng từ một)")
    public SseEmitter askStream(
            @RequestParam String question,
            @RequestParam(required = false) String sessionId) {
        
        SseEmitter emitter = new SseEmitter(300_000L); // 5 phút timeout
        
        aiTaskExecutor.execute(() -> {
            try {
                AiAskRequest req = new AiAskRequest();
                req.setQuestion(question);
                req.setSessionId(sessionId);

                aiService.askStream(req, fragment -> {
                    try {
                        emitter.send(fragment);
                    } catch (IOException e) {
                        log.error("SSE Send Error: {}", e.getMessage());
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming Error: ", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
