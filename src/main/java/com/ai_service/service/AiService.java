package com.ai_service.service;

import com.ai_service.client.OllamaClient;
import com.ai_service.dto.AiAskRequest;
import com.ai_service.dto.AiAskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final OllamaClient ollamaClient;
    private final AiHistoryService historyService;
    private final AiAuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?i)(?:SQL\\s*:\\s*)?```\\s*sql\\s*([\\s\\S]*?)```|\\b(SELECT|WITH)\\b[\\s\\S]*?(?=\\n\\n|$)",
            Pattern.CASE_INSENSITIVE);

    // ==================== API SYNC ====================
    public AiAskResponse ask(AiAskRequest req) {
        String sessionId = req.getSessionId();
        String userMessage = getUserMessage(req);
        long start = System.currentTimeMillis();

        String sql = null;
        int rowsReturned = 0;
        String error = null;
        String finalReply = null;

        try {
            // Bước 1: Sinh SQL
            log.info("AI ask userMessage: {}", userMessage);
            String rawSql = ollamaClient.generateSql(buildSqlPrompt(userMessage));
            log.info("AI raw SQL response from Ollama: {}", rawSql);
            sql = extractSql(rawSql);
            log.info("AI extracted SQL: {}", sql);

            if (sql == null || !isSafeSelect(sql)) {
                log.warn("AI SQL is empty or unsafe. Fallback to general answer. sql={}", sql);
                finalReply = ollamaClient.generateAnswer(buildGeneralPrompt(userMessage));
                return new AiAskResponse(finalReply, null);
            }

            // Bước 2: Chạy SQL thật
            log.info("AI executing SQL: {}", sql);
            List<Map<String, Object>> result = executeSafeQuery(sql);
            rowsReturned = result.size();
            log.info("AI SQL rows returned: {}", rowsReturned);

            // Bước 3: Sinh câu trả lời cuối cùng
            finalReply = ollamaClient.generateAnswer(buildAnswerPrompt(userMessage, sql, result));
            log.info("AI final answer: {}", finalReply);

            return new AiAskResponse(finalReply, null);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("AI Service error", e);
            finalReply = "Rất tiếc, hiện tại tôi chưa thể truy vấn dữ liệu. Bạn thử hỏi lại sau nhé!";
            return new AiAskResponse(finalReply, error);
        } finally {
            auditService.log(sessionId, userMessage, sql, rowsReturned, error, System.currentTimeMillis() - start);
            historyService.addHistory(sessionId, userMessage, finalReply);
        }
    }

    // ==================== API STREAM ====================
    public void askStream(AiAskRequest req, java.util.function.Consumer<String> fragmentConsumer) {
        String sessionId = req.getSessionId();
        String userMessage = getUserMessage(req);
        long start = System.currentTimeMillis();

        String sql = null;
        int rowsReturned = 0;
        String error = null;

        try {
            log.info("AI stream userMessage: {}", userMessage);
            String rawSql = ollamaClient.generateSql(buildSqlPrompt(userMessage));
            log.info("AI stream raw SQL response from Ollama: {}", rawSql);
            sql = extractSql(rawSql);
            log.info("AI stream extracted SQL: {}", sql);

            if (sql == null || !isSafeSelect(sql)) {
                log.warn("AI stream SQL is empty or unsafe. Fallback to general answer. sql={}", sql);
                ollamaClient.generateAnswerStream(buildGeneralPrompt(userMessage), fragmentConsumer);
                return;
            }

            log.info("AI stream executing SQL: {}", sql);
            List<Map<String, Object>> result = executeSafeQuery(sql);
            rowsReturned = result.size();
            log.info("AI stream SQL rows returned: {}", rowsReturned);
            log.info("AI stream SQL result: {}", result);

            ollamaClient.generateAnswerStream(buildAnswerPrompt(userMessage, sql, result), fragmentConsumer);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("Stream error", e);
            fragmentConsumer.accept("Rất tiếc, hiện tại tôi chưa thể truy vấn dữ liệu.");
        } finally {
            auditService.log(sessionId, userMessage, sql, rowsReturned, error, System.currentTimeMillis() - start);
        }
    }

    // ==================== CÁC METHOD HỖ TRỢ ====================
    private String buildSqlPrompt(String userMessage) {
        return """
                <|im_start|>system
                Bạn là bộ chuyển câu hỏi tiếng Việt thành SQL PostgreSQL cho StockMaster-WMS.

                QUY TẮC BẮT BUỘC:
                - Chỉ trả về đúng 1 câu SQL thuần, không markdown, không giải thích, không văn bản tiếng Việt.
                - SQL phải bắt đầu bằng SELECT hoặc WITH.
                - Chỉ dùng truy vấn đọc dữ liệu. Không INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE.
                - Nếu người dùng hỏi "bao nhiêu" về một danh sách đối tượng như kho, vị trí, tồn kho, đơn hàng, hãy trả về các dòng chi tiết để hệ thống có thể đếm và liệt kê.
                - Chỉ dùng COUNT(*) khi người dùng hỏi rõ "chỉ cho biết số lượng", "tổng số", hoặc dữ liệu chi tiết không cần thiết.
                - Nếu không chắc câu hỏi dùng bảng nào, trả về: SELECT 'UNSUPPORTED_QUESTION' AS reason WHERE FALSE

                SCHEMA CHO PHÉP:
                warehouses(id, code, name, address, timezone, is_active, created_at, manager_name, updated_at)
                locations(id, warehouse_id, code, zone, aisle, rack, level, bin, location_type, status, is_active, created_at)
                stock_levels(id, warehouse_id, location_id, product_id, lot_number, expiry_date, qty_on_hand, qty_reserved, qty_available, updated_at)

                VÍ DỤ:
                User: Hệ thống hiện có bao nhiêu kho hàng?
                Assistant: SELECT code, name, address, manager_name, is_active FROM warehouses ORDER BY name

                User: Chỉ cho biết tổng số kho hàng
                Assistant: SELECT COUNT(*) AS total_warehouses FROM warehouses
                <|im_end|>
                <|im_start|>user
                %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(userMessage);
    }

    private String buildGeneralPrompt(String userMessage) {
        return """
                <|im_start|>system
                Bạn là AI Trợ lý thông minh của StockMaster-WMS. Trả lời tự nhiên, ngắn gọn, chuyên nghiệp bằng tiếng Việt.
                <|im_end|>
                <|im_start|>user
                %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(userMessage);
    }

    private String buildAnswerPrompt(String userMessage, String sql, List<Map<String, Object>> result) {
        return """
                <|im_start|>system
                Bạn là AI Trợ lý kho hàng StockMaster-WMS.
                Dựa vào SQL và kết quả database dạng JSON, trả lời trực tiếp cho người dùng bằng tiếng Việt.
                Không trả lại SQL. Không nói về kỹ thuật.
                Chỉ được dùng dữ liệu có trong JSON. Không bịa tên, mã, ID, nhà cung cấp, địa chỉ, người quản lý hoặc trạng thái nếu JSON không có.
                Trả lời ngắn gọn nhưng đủ ý theo đúng câu hỏi. Không thêm trạng thái, địa chỉ, mã kho hoặc ngày tháng nếu người dùng không hỏi.
                Nếu kết quả là danh sách kho và có manager_name, hãy trả lời một câu theo mẫu: "Tồn kho hiện tại là N. Kho A do X quản lý và Kho B do Y quản lý."
                Với tên kho dạng "Kho trung tâm Hà Nội" hoặc "Kho trung tâm TPHCM", có thể diễn đạt ngắn là "Kho Hà Nội" hoặc "Kho TP.HCM".
                Nếu kết quả là COUNT thì nêu số lượng rõ ràng. Nếu kết quả là danh sách, không bỏ qua các dòng quan trọng.
                <|im_end|>
                <|im_start|>user
                %s
                <|im_end|>
                <|im_start|>assistant
                SQL: %s
                <|im_end|>
                <|im_start|>tool
                Kết quả database JSON: %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(userMessage, sql, toJson(result));
    }

    private String toJson(List<Map<String, Object>> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize AI SQL result to JSON", e);
            return result.toString();
        }
    }

    private String extractSql(String text) {
        if (text == null || text.isBlank()) return null;
        var matcher = SQL_PATTERN.matcher(text);
        if (matcher.find()) {
            String sql = matcher.group(1) != null ? matcher.group(1) : matcher.group(0);
            return sql.trim();
        }
        return text.trim();
    }

    private boolean isSafeSelect(String sql) {
        if (sql == null) return false;
        String lower = sql.toLowerCase().trim();
        return (lower.startsWith("select") || lower.startsWith("with"))
                && !lower.matches("(?s).*(insert|update|delete|drop|alter|truncate|create|grant|revoke|exec|call).*");
    }

    private List<Map<String, Object>> executeSafeQuery(String sql) {
        String clean = sql.trim();
        if (clean.endsWith(";")) clean = clean.substring(0, clean.length() - 1);
        if (!clean.toLowerCase().contains(" limit ")) clean += " LIMIT 100";
        return jdbcTemplate.queryForList(clean);
    }

    private String getUserMessage(AiAskRequest req) {
        return req.getMessage() != null && !req.getMessage().isBlank()
                ? req.getMessage() : req.getQuestion();
    }
}
