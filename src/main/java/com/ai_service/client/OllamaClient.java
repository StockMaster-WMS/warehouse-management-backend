package com.ai_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class OllamaClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OllamaClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.ollama.api-url:http://localhost:11434}") String apiUrl,
            @Value("${ai.ollama.model:stockmaster-ai}") String model,
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
    public String generateIntent(String prompt) {
        return generate(prompt, 0.0, 0.1);
    }

    // Sinh câu trả lời tự nhiên từ prompt đã format.
    public String generateAnswer(String prompt) {
        return generate(prompt, 0.2, 0.3);
    }

    // Gửi prompt raw tới Ollama và lấy response dạng text.
    private String generate(String prompt, double temperature, double topP) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "raw", true,
                "options", Map.of(
                        "temperature", temperature,
                        "top_p", topP,
                        "num_ctx", 8192,
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
        return generatedText instanceof String text ? text : "";
    }

    // Stream câu trả lời từ /api/generate cho prompt raw.
    public void generateAnswerStream(String prompt, java.util.function.Consumer<String> consumer) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", true,
                "raw", true,
                "options", Map.of(
                        "temperature", 0.2,
                        "top_p", 0.3,
                        "num_ctx", 8192,
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
                            String content = extractGenerateContent(line);
                            if (!content.isEmpty()) {
                                consumer.accept(content);
                            }
                        }
                    }
                    return null;
                });
    }

    // Gọi API chat không stream với cấu hình sampling tùy chỉnh.
    private String chat(List<Map<String, String>> messages, double temperature, double topP) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", false,
                "options", Map.of(
                        "temperature", temperature,
                        "top_p", topP,
                        "num_ctx", 8192
                )
        );

        Map<?, ?> response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        Map<?, ?> message = (Map<?, ?>) response.get("message");
        return message != null ? (String) message.get("content") : "";
    }

    // Gọi API chat dạng stream.
    public void chatStream(List<Map<String, String>> messages, java.util.function.Consumer<String> consumer) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", true,
                "options", Map.of(
                        "temperature", 0.7,
                        "top_p", 0.9,
                        "num_ctx", 8192
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
                            String content = extractContent(line);
                            if (!content.isEmpty()) {
                                consumer.accept(content);
                            }
                        }
                    }
                    return null;
                });
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
                    throw e;
                }
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
