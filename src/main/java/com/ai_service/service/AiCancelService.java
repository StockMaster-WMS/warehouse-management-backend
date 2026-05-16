package com.ai_service.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AiCancelService {

    private final Map<String, AtomicBoolean> tokens = new ConcurrentHashMap<>();

    public void startSession(String sessionId) {
        if (sessionId == null) return;
        tokens.put(sessionId, new AtomicBoolean(false));
    }

    public void cancel(String sessionId) {
        if (sessionId == null) return;
        tokens.computeIfPresent(sessionId, (k, v) -> {
            v.set(true);
            return v;
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
