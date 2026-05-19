package com.ai_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
@Slf4j
public class OllamaClient implements AiTextClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int numCtx;
    private final String keepAlive;

    public OllamaClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.ollama.api-url:http://localhost:11434}") String apiUrl,
            @Value("${ai.ollama.model:stockmaster-ai}") String model,
            @Value("${ai.ollama.num-ctx:4096}") int numCtx,
            @Value("${ai.ollama.keep-alive:30m}") String keepAlive,
            @Value("${ai.ollama.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${ai.ollama.read-timeout-seconds:120}") long readTimeoutSeconds) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.numCtx = numCtx;
        this.keepAlive = keepAlive;
    }

    // Gọi API chat không stream với tham số mặc định.
    public String chat(List<Map<String, String>> messages) {
        return chat(messages, 0.7, 0.9);
    }

    // Gọi model ở chế độ ổn định hơn để sinh SQL.
    public String generateSql(String prompt) {
        return generate(prompt, 0.0, 0.1);
    }

    // Sinh JSON intent ổn định để backend gọi tool cố định.
    @Override
    public String generateIntent(String prompt) {
        return generate(prompt, 0.0, 0.1, 160);
    }

    // Sinh câu trả lời tự nhiên từ prompt đã format.
    @Override
    public String generateAnswer(String prompt) {
        return generate(prompt, 0.2, 0.3, 512);
    }

    // Gửi prompt raw tới Ollama và lấy response dạng text.
    private String generate(String prompt, double temperature, double topP) {
        return generate(prompt, temperature, topP, 512);
    }

    private String generate(String prompt, double temperature, double topP, int numPredict) {
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        log.info("AI ollama generate start model={} temp={} topP={} numPredict={} promptChars={}",
                selectedModel, temperature, topP, numPredict, prompt == null ? 0 : prompt.length());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "prompt", prompt,
                "stream", false,
                "raw", true,
                "keep_alive", keepAlive,
                "options", Map.of(
                        "temperature", temperature,
                        "top_p", topP,
                        "num_ctx", numCtx,
                        "num_predict", numPredict,
                        "stop", List.of("<|im_end|>", "<|endoftext|>")
                )
        );

        Map<?, ?> response = executeWithRetry(() -> restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class), 2);

        Object generatedText = response != null ? response.get("response") : null;
        String text = generatedText instanceof String value ? value : "";
        log.info("AI ollama generate done model={} outputChars={} durationMs={}",
                selectedModel, text.length(), System.currentTimeMillis() - start);
        return text;
    }

    // Stream câu trả lời từ /api/generate cho prompt raw.
    @Override
    public void generateAnswerStream(String prompt, java.util.function.Consumer<String> consumer, java.util.function.Supplier<Boolean> isCancelled) {
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        int[] chunks = {0};
        int[] chars = {0};
        log.info("AI ollama stream start model={} numPredict={} promptChars={}",
                selectedModel, 512, prompt == null ? 0 : prompt.length());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "prompt", prompt,
                "stream", true,
                "raw", true,
                "keep_alive", keepAlive,
                "options", Map.of(
                        "temperature", 0.2,
                        "top_p", 0.3,
                        "num_ctx", numCtx,
                        "num_predict", 512,
                        "stop", List.of("<|im_end|>", "<|endoftext|>")
                )
        );

        restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    try (var reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.getBody()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            if (isCancelled != null && isCancelled.get()) {
                                break;
                            }
                            String content = extractGenerateContent(line);
                            if (!content.isEmpty()) {
                                chunks[0]++;
                                chars[0] += content.length();
                                consumer.accept(content);
                            }
                        }
                    }
                    return null;
                });
        log.info("AI ollama stream done model={} chunks={} outputChars={} cancelled={} durationMs={}",
                selectedModel, chunks[0], chars[0], isCancelled != null && isCancelled.get(),
                System.currentTimeMillis() - start);
    }

    // Gọi API chat không stream với cấu hình sampling tùy chỉnh.
    private String chat(List<Map<String, String>> messages, double temperature, double topP) {
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        log.info("AI ollama chat start model={} temp={} topP={} messages={}",
                selectedModel, temperature, topP, messages == null ? 0 : messages.size());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "messages", messages,
                "stream", false,
                "keep_alive", keepAlive,
                "options", Map.of(
                        "temperature", temperature,
                        "top_p", topP,
                        "num_ctx", numCtx,
                        "num_predict", 512
                )
        );

        Map<?, ?> response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        Map<?, ?> message = (Map<?, ?>) response.get("message");
        String content = message != null ? (String) message.get("content") : "";
        log.info("AI ollama chat done model={} outputChars={} durationMs={}",
                selectedModel, content == null ? 0 : content.length(), System.currentTimeMillis() - start);
        return content == null ? "" : content;
    }

    // Gọi API chat dạng stream.
    public void chatStream(List<Map<String, String>> messages, java.util.function.Consumer<String> consumer, java.util.function.Supplier<Boolean> isCancelled) {
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        int[] chunks = {0};
        int[] chars = {0};
        log.info("AI ollama chatStream start model={} messages={}",
                selectedModel, messages == null ? 0 : messages.size());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "messages", messages,
                "stream", true,
                "keep_alive", keepAlive,
                "options", Map.of(
                        "temperature", 0.7,
                        "top_p", 0.9,
                        "num_ctx", numCtx,
                        "num_predict", 512
                )
        );

        restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    try (var reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.getBody()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            if (isCancelled != null && isCancelled.get()) {
                                break;
                            }
                            String content = extractContent(line);
                            if (!content.isEmpty()) {
                                chunks[0]++;
                                chars[0] += content.length();
                                consumer.accept(content);
                            }
                        }
                    }
                    return null;
                });
        log.info("AI ollama chatStream done model={} chunks={} outputChars={} cancelled={} durationMs={}",
                selectedModel, chunks[0], chars[0], isCancelled != null && isCancelled.get(),
                System.currentTimeMillis() - start);
    }

    private String activeModel() {
        String override = AiModelSelectionContext.model();
        return StringUtils.hasText(override) ? override.trim() : model;
    }

    // Trích nội dung từ từng dòng JSON của /api/chat.
    private String extractContent(String jsonLine) {
        try {
            var node = objectMapper.readTree(jsonLine);
            return node.path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    // Trích nội dung từ từng dòng JSON của /api/generate.
    private String extractGenerateContent(String jsonLine) {
        try {
            var node = objectMapper.readTree(jsonLine);
            return node.path("response").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    // Thử lại request Ollama khi lỗi tạm thời.
    private <T> T executeWithRetry(Supplier<T> supplier, int maxAttempts) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt == maxAttempts) {
                    log.warn("AI ollama request failed attempt={}/{} error={}",
                            attempt, maxAttempts, e.getMessage());
                    throw e;
                }
                log.warn("AI ollama request retry attempt={}/{} error={}",
                        attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last == null ? new IllegalStateException("Ollama request failed") : last;
    }
}
