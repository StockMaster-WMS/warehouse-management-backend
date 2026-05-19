package com.ai_service.client;

import com.ai_service.service.AiProviderConfigService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
@Slf4j
public class OpenAiCompatibleClient implements AiTextClient {

    private static final Pattern CHATML_BLOCK_PATTERN = Pattern.compile(
            "<\\|im_start\\|>(system|user|assistant)\\s*\\R([\\s\\S]*?)<\\|im_end\\|>");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigService providerConfigService;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AiProviderConfigService providerConfigService,
            @Value("${ai.openai.api-url:https://api.openai.com/v1}") String apiUrl,
            @Value("${ai.openai.api-key:}") String apiKey,
            @Value("${ai.openai.model:gpt-4o-mini}") String model,
            @Value("${ai.openai.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${ai.openai.read-timeout-seconds:120}") long readTimeoutSeconds) {

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
        this.model = model;
    }

    @Override
    public String generateIntent(String prompt) {
        return complete(prompt, 0.0, 0.1, 160);
    }

    @Override
    public String generateAnswer(String prompt) {
        return complete(prompt, 0.2, 0.3, 512);
    }

    @Override
    public void generateAnswerStream(String prompt, Consumer<String> consumer, Supplier<Boolean> isCancelled) {
        String selectedApiKey = activeApiKey();
        requireApiKey(selectedApiKey);
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        int[] chunks = {0};
        int[] chars = {0};
        log.info("AI openai-compatible stream start model={} promptChars={}",
                selectedModel, prompt == null ? 0 : prompt.length());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "messages", toMessages(prompt),
                "stream", true,
                "temperature", 0.2,
                "top_p", 0.3,
                "max_tokens", 512,
                "stop", List.of("<|im_end|>", "<|endoftext|>")
        );

        restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + selectedApiKey)
                .body(body)
                .exchange((request, response) -> {
                    try (var reader = new BufferedReader(new InputStreamReader(response.getBody()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank() || !line.startsWith("data:")) {
                                continue;
                            }
                            if (isCancelled != null && isCancelled.get()) {
                                break;
                            }
                            String payload = line.substring("data:".length()).trim();
                            if ("[DONE]".equals(payload)) {
                                break;
                            }
                            String content = extractStreamContent(payload);
                            if (!content.isEmpty()) {
                                chunks[0]++;
                                chars[0] += content.length();
                                consumer.accept(content);
                            }
                        }
                    }
                    return null;
                });

        log.info("AI openai-compatible stream done model={} chunks={} outputChars={} cancelled={} durationMs={}",
                selectedModel, chunks[0], chars[0], isCancelled != null && isCancelled.get(),
                System.currentTimeMillis() - start);
    }

    private String complete(String prompt, double temperature, double topP, int maxTokens) {
        String selectedApiKey = activeApiKey();
        requireApiKey(selectedApiKey);
        String selectedModel = activeModel();
        long start = System.currentTimeMillis();
        log.info("AI openai-compatible complete start model={} temp={} topP={} maxTokens={} promptChars={}",
                selectedModel, temperature, topP, maxTokens, prompt == null ? 0 : prompt.length());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "messages", toMessages(prompt),
                "stream", false,
                "temperature", temperature,
                "top_p", topP,
                "max_tokens", maxTokens,
                "stop", List.of("<|im_end|>", "<|endoftext|>")
        );

        Map<?, ?> response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + selectedApiKey)
                .body(body)
                .retrieve()
                .body(Map.class);

        String text = extractCompletionContent(response);
        log.info("AI openai-compatible complete done model={} outputChars={} durationMs={}",
                selectedModel, text.length(), System.currentTimeMillis() - start);
        return text;
    }

    private List<Map<String, String>> toMessages(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (prompt != null) {
            var matcher = CHATML_BLOCK_PATTERN.matcher(prompt);
            while (matcher.find()) {
                String role = matcher.group(1);
                String content = matcher.group(2).trim();
                if (!content.isEmpty()) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }
        if (messages.isEmpty()) {
            messages.add(Map.of("role", "user", "content", prompt == null ? "" : prompt));
        }
        return messages;
    }

    private String extractCompletionContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return "";
        }
        Object messageObject = choice.get("message");
        if (!(messageObject instanceof Map<?, ?> message)) {
            return "";
        }
        Object content = message.get("content");
        return content instanceof String value ? value : "";
    }

    private String extractStreamContent(String jsonLine) {
        try {
            var node = objectMapper.readTree(jsonLine);
            return node.path("choices").path(0).path("delta").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private void requireApiKey(String selectedApiKey) {
        if (!StringUtils.hasText(selectedApiKey)) {
            throw new IllegalStateException("AI_OPENAI_API_KEY is required when AI_PROVIDER=openai or openai-compatible");
        }
    }

    private String activeApiKey() {
        return providerConfigService.findApiKey(AiProviderConfigService.OPENAI_PROVIDER)
                .orElse(apiKey);
    }

    private String activeModel() {
        String override = AiModelSelectionContext.model();
        return StringUtils.hasText(override) ? override.trim() : model;
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://api.openai.com/v1";
        }
        return value.replaceAll("/+$", "");
    }
}
