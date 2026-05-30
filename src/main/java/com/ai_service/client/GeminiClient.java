package com.ai_service.client;

import com.ai_service.service.provider.AiProviderConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
@Slf4j
public class GeminiClient implements AiTextClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigService providerConfigService;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AiProviderConfigService providerConfigService,
            @Value("${ai.gemini.api-url:https://generativelanguage.googleapis.com/v1beta}") String apiUrl,
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${ai.gemini.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${ai.gemini.read-timeout-seconds:120}") long readTimeoutSeconds) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = restClientBuilder
                .baseUrl(stripTrailingSlash(apiUrl))
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.providerConfigService = providerConfigService;
        this.apiKey = apiKey;
        this.model = normalizeModel(model);
    }

    @Override
    public String generateIntent(String prompt) {
        return generate(prompt, 0.0, 0.1, 160);
    }

    @Override
    public String generateAnswer(String prompt) {
        return generate(prompt, 0.2, 0.3, 512);
    }

    @Override
    public void generateAnswerStream(String prompt, Consumer<String> consumer, Supplier<Boolean> isCancelled) {
        String selectedApiKey = activeApiKey();
        requireApiKey(selectedApiKey);
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        int[] chunks = {0};
        int[] chars = {0};
        log.info("AI gemini stream start model={} promptChars={}",
                selectedModel, prompt == null ? 0 : prompt.length());

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .build(selectedModel))
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", selectedApiKey)
                .body(requestBody(prompt, 0.2, 0.3, 512))
                .exchange((request, response) -> {
                    try (var reader = new BufferedReader(new InputStreamReader(response.getBody()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) {
                                continue;
                            }
                            if (isCancelled != null && isCancelled.get()) {
                                break;
                            }
                            String payload = line.startsWith("data:")
                                    ? line.substring("data:".length()).trim()
                                    : line.trim();
                            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                                continue;
                            }
                            String content = extractText(payload);
                            if (!content.isEmpty()) {
                                chunks[0]++;
                                chars[0] += content.length();
                                consumer.accept(content);
                            }
                        }
                    }
                    return null;
                });

        log.info("AI gemini stream done model={} chunks={} outputChars={} cancelled={} durationMs={}",
                selectedModel, chunks[0], chars[0], isCancelled != null && isCancelled.get(),
                System.currentTimeMillis() - start);
    }

    private String generate(String prompt, double temperature, double topP, int maxOutputTokens) {
        String selectedApiKey = activeApiKey();
        requireApiKey(selectedApiKey);
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        log.info("AI gemini generate start model={} temp={} topP={} maxOutputTokens={} promptChars={}",
                selectedModel, temperature, topP, maxOutputTokens, prompt == null ? 0 : prompt.length());

        Map<?, ?> response = restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/models/{model}:generateContent").build(selectedModel))
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", selectedApiKey)
                .body(requestBody(prompt, temperature, topP, maxOutputTokens))
                .retrieve()
                .body(Map.class);

        String text = extractText(response);
        log.info("AI gemini generate done model={} outputChars={} durationMs={}",
                selectedModel, text.length(), System.currentTimeMillis() - start);
        return text;
    }

    private Map<String, Object> requestBody(String prompt, double temperature, double topP, int maxOutputTokens) {
        return Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt == null ? "" : prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "topP", topP,
                        "maxOutputTokens", maxOutputTokens,
                        "stopSequences", List.of("<|im_end|>", "<|endoftext|>")
                )
        );
    }

    private String extractText(Object response) {
        if (response == null) {
            return "";
        }
        try {
            JsonNode root = response instanceof String text
                    ? objectMapper.readTree(text)
                    : objectMapper.valueToTree(response);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray()) {
                return "";
            }
            StringBuilder output = new StringBuilder();
            for (JsonNode part : parts) {
                output.append(part.path("text").asText(""));
            }
            return output.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void requireApiKey(String selectedApiKey) {
        if (!StringUtils.hasText(selectedApiKey)) {
            throw new IllegalStateException("AI_GEMINI_API_KEY is required when AI_PROVIDER=gemini or google");
        }
    }

    private String activeApiKey() {
        return providerConfigService.findApiKey(AiProviderConfigService.GEMINI_PROVIDER)
                .orElse(apiKey);
    }

    private String normalizeModel(String value) {
        String model = StringUtils.hasText(value) ? value.trim() : "gemini-2.5-flash";
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    private String activeModel() {
        String override = AiModelSelectionContext.model();
        return normalizeModel(StringUtils.hasText(override) ? override : model);
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }
        return value.replaceAll("/+$", "");
    }
}
