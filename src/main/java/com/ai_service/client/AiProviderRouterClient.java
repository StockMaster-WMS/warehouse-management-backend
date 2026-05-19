package com.ai_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Primary
@Component
@Slf4j
public class AiProviderRouterClient implements AiTextClient {

    private final String provider;
    private final OllamaClient ollamaClient;
    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final GeminiClient geminiClient;

    public AiProviderRouterClient(
            @Value("${ai.provider:ollama}") String provider,
            OllamaClient ollamaClient,
            OpenAiCompatibleClient openAiCompatibleClient,
            GeminiClient geminiClient) {
        this.provider = provider == null ? "ollama" : provider.trim().toLowerCase(Locale.ROOT);
        this.ollamaClient = ollamaClient;
        this.openAiCompatibleClient = openAiCompatibleClient;
        this.geminiClient = geminiClient;
    }

    @Override
    public String generateIntent(String prompt) {
        return activeClient().generateIntent(prompt);
    }

    @Override
    public String generateAnswer(String prompt) {
        return activeClient().generateAnswer(prompt);
    }

    @Override
    public void generateAnswerStream(String prompt, Consumer<String> consumer, Supplier<Boolean> isCancelled) {
        activeClient().generateAnswerStream(prompt, consumer, isCancelled);
    }

    private AiTextClient activeClient() {
        String requestedProvider = AiModelSelectionContext.provider();
        String selectedProvider = requestedProvider == null
                ? provider
                : requestedProvider.trim().toLowerCase(Locale.ROOT);
        if (!isSupportedProvider(selectedProvider)) {
            log.warn("Unknown AI provider='{}', falling back to configured provider='{}'",
                    selectedProvider, provider);
            selectedProvider = provider;
        }
        return switch (selectedProvider) {
            case "ollama" -> ollamaClient;
            case "openai", "openai-compatible" -> openAiCompatibleClient;
            case "gemini", "google", "google-ai-studio" -> geminiClient;
            default -> {
                log.warn("Unknown configured AI_PROVIDER='{}', falling back to ollama", provider);
                yield ollamaClient;
            }
        };
    }

    private boolean isSupportedProvider(String value) {
        return switch (value) {
            case "ollama", "openai", "openai-compatible", "gemini", "google", "google-ai-studio" -> true;
            default -> false;
        };
    }
}
