package com.ai_service.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Queue;

@Service
public class AiHistoryService {

    // Lưu lịch sử theo user + session. Mỗi session giữ tối đa 5 lượt hội thoại gần nhất.
    private final Map<String, SessionHistory> historyMap = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 10;
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    // Thêm một lượt hỏi đáp vào lịch sử session.
    public void addHistory(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) return;

        cleanupExpiredSessions();
        SessionHistory session = historyMap.computeIfAbsent(sessionKey(sessionId), k -> new SessionHistory());
        synchronized (session) {
            session.touch();
            while (session.messages.size() > MAX_HISTORY - 2) {
                session.messages.poll(); // Xóa lượt cũ nhất
            }
            session.messages.add(Map.of("role", "user", "content", question));
            session.messages.add(Map.of("role", "assistant", "content", answer));
        }
    }

    // Lấy lịch sử session dạng text đơn giản.
    public String getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        List<Map<String, String>> history = getMessages(sessionId);
        if (history.isEmpty()) return null;
        return history.stream()
                .map(message -> message.getOrDefault("role", "user") + ": " + message.getOrDefault("content", ""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }

    // Lấy lịch sử session dạng danh sách message.
    public List<Map<String, String>> getMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        cleanupExpiredSessions();
        SessionHistory session = historyMap.get(sessionKey(sessionId));
        if (session == null) return List.of();
        synchronized (session) {
            if (session.expired()) {
                historyMap.remove(sessionKey(sessionId), session);
                return List.of();
            }
            session.touch();
            return new ArrayList<>(session.messages);
        }
    }

    // Xóa lịch sử của một session.
    public void clearHistory(String sessionId) {
        if (sessionId != null) historyMap.remove(sessionKey(sessionId));
    }

    // Lấy câu trả lời gần nhất tương ứng với một câu hỏi nếu trùng khớp hoàn toàn.
    public String findLastAnswerForQuestion(String sessionId, String question) {
        if (sessionId == null || sessionId.isBlank() || question == null) return null;
        List<Map<String, String>> list = getMessages(sessionId);
        if (list.isEmpty()) return null;

        for (int i = list.size() - 1; i >= 1; i--) {
            Map<String, String> entry = list.get(i - 1);
            Map<String, String> reply = list.get(i);
            if ("user".equals(entry.get("role")) && "assistant".equals(reply.get("role"))) {
                String q = entry.getOrDefault("content", "");
                String a = reply.getOrDefault("content", "");
                if (q.equals(question)) {
                    return a;
                }
            }
            i--; // skip pair
        }
        return null;
    }

    private String sessionKey(String sessionId) {
        return currentUserKey() + ":" + sessionId.trim();
    }

    private String currentUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private void cleanupExpiredSessions() {
        historyMap.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private static final class SessionHistory {
        private final Queue<Map<String, String>> messages = new LinkedList<>();
        private Instant lastAccessedAt = Instant.now();

        private void touch() {
            lastAccessedAt = Instant.now();
        }

        private boolean expired() {
            return lastAccessedAt.plus(SESSION_TTL).isBefore(Instant.now());
        }
    }
}
