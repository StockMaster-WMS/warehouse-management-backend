package com.ai_service.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Queue;

@Service
public class AiHistoryService {

    // Lưu trữ lịch sử theo Session ID. Mỗi session giữ tối đa 5 lượt hội thoại gần nhất.
    private final Map<String, Queue<Map<String, String>>> historyMap = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 10;

    public void addHistory(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) return;
        
        Queue<Map<String, String>> history = historyMap.computeIfAbsent(sessionId, k -> new LinkedList<>());
        while (history.size() > MAX_HISTORY - 2) {
            history.poll(); // Xóa lượt cũ nhất
        }
        history.add(Map.of("role", "user", "content", question));
        history.add(Map.of("role", "assistant", "content", answer));
    }

    public String getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        Queue<Map<String, String>> history = historyMap.get(sessionId);
        if (history == null || history.isEmpty()) return null;
        return history.stream()
                .map(message -> message.getOrDefault("role", "user") + ": " + message.getOrDefault("content", ""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }

    public List<Map<String, String>> getMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        Queue<Map<String, String>> history = historyMap.get(sessionId);
        if (history == null || history.isEmpty()) return List.of();
        return new ArrayList<>(history);
    }

    public void clearHistory(String sessionId) {
        if (sessionId != null) historyMap.remove(sessionId);
    }
}
