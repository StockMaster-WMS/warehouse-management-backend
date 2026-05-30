package com.ai_service.service.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AiCancelService {

    private final Map<String, AtomicBoolean> tokens = new ConcurrentHashMap<>();

    public void startSession(String sessionId) {
        if (sessionId == null) return;
        tokens.compute(sessionId, (key, existing) -> existing == null ? new AtomicBoolean(false) : existing);
    }

    public void cancel(String sessionId) {
        if (sessionId == null) return;
        tokens.compute(sessionId, (key, existing) -> {
            AtomicBoolean token = existing == null ? new AtomicBoolean(false) : existing;
            token.set(true);
            return token;
        });
    }

    public boolean isCancelled(String sessionId) {
        if (sessionId == null) return false;
        AtomicBoolean b = tokens.get(sessionId);
        return b != null && b.get();
    }

    public void clear(String sessionId) {
        if (sessionId == null) return;
        tokens.remove(sessionId);
    }
}
