package com.ai_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OllamaClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OllamaClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.ollama.api-url:http://localhost:11434}") String apiUrl,
            @Value("${ai.ollama.model:stockmaster-ai}") String model) {

        this.restClient = restClientBuilder.baseUrl(apiUrl).build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    /**
     * Gọi API chat (khuyến nghị cho Qwen2.5)
     */
    public String chat(List<Map<String, String>> messages) {
        return chat(messages, 0.7, 0.9);
    }

    /**
     * Gọi model ở chế độ ổn định hơn để sinh SQL.
     */
    public String generateSql(String prompt) {
        return generate(prompt, 0.0, 0.1);
    }

    /**
     * Sinh câu trả lời tự nhiên từ prompt đã format theo Qwen chat template.
     */
    public String generateAnswer(String prompt) {
        return generate(prompt, 0.2, 0.3);
    }

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

        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        Object generatedText = response != null ? response.get("response") : null;
        return generatedText instanceof String text ? text : "";
    }

    /**
     * Stream câu trả lời từ /api/generate. Dùng cho prompt raw Qwen.
     */
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

    /**
     * Stream version
     */
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

    private String extractContent(String jsonLine) {
        try {
            var node = objectMapper.readTree(jsonLine);
            return node.path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String extractGenerateContent(String jsonLine) {
        try {
            var node = objectMapper.readTree(jsonLine);
            return node.path("response").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
