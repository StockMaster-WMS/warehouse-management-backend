package com.ai_service.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface AiTextClient {

    String generateIntent(String prompt);

    String generateAnswer(String prompt);

    void generateAnswerStream(String prompt, Consumer<String> consumer, Supplier<Boolean> isCancelled);
}
