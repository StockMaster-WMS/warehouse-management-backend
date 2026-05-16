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

    // Thêm một lượt hỏi đáp vào lịch sử session.
    public void addHistory(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank()) return;
        
        Queue<Map<String, String>> history = historyMap.computeIfAbsent(sessionId, k -> new LinkedList<>());
        while (history.size() > MAX_HISTORY - 2) {
            history.poll(); // Xóa lượt cũ nhất
        }
        history.add(Map.of("role", "user", "content", question));
        history.add(Map.of("role", "assistant", "content", answer));
    }

    // Lấy lịch sử session dạng text đơn giản.
    public String getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        Queue<Map<String, String>> history = historyMap.get(sessionId);
        if (history == null || history.isEmpty()) return null;
        return history.stream()
                .map(message -> message.getOrDefault("role", "user") + ": " + message.getOrDefault("content", ""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }

    // Lấy lịch sử session dạng danh sách message.
    public List<Map<String, String>> getMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        Queue<Map<String, String>> history = historyMap.get(sessionId);
        if (history == null || history.isEmpty()) return List.of();
        return new ArrayList<>(history);
    }

    // Xóa lịch sử của một session.
    public void clearHistory(String sessionId) {
        if (sessionId != null) historyMap.remove(sessionId);
    }

    // Lấy câu trả lời gần nhất tương ứng với một câu hỏi nếu trùng khớp hoàn toàn.
    public String findLastAnswerForQuestion(String sessionId, String question) {
        if (sessionId == null || sessionId.isBlank() || question == null) return null;
        Queue<Map<String, String>> history = historyMap.get(sessionId);
        if (history == null || history.isEmpty()) return null;

        // traverse from tail: since Queue doesn't support reverse, convert to list
        List<Map<String, String>> list = new ArrayList<>(history);
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
}
