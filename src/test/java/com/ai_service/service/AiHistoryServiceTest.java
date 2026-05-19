package com.ai_service.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AiHistoryServiceTest {

    private final AiHistoryService service = new AiHistoryService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void historyIsScopedByAuthenticatedUser() {
        authenticate("user-a");
        service.addHistory("same-session", "question-a", "answer-a");

        authenticate("user-b");
        assertThat(service.getMessages("same-session")).isEmpty();

        authenticate("user-a");
        assertThat(service.getMessages("same-session")).hasSize(2);
        assertThat(service.findLastAnswerForQuestion("same-session", "question-a")).isEqualTo("answer-a");
    }

    private static void authenticate(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, "n/a"));
    }
}
