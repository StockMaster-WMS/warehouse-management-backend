package com.ai_service.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAuditService {

    private final JdbcTemplate jdbcTemplate;

    // Tạo bảng audit log nếu database chưa có.
    @PostConstruct
    void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_audit_logs (
                    id BIGSERIAL PRIMARY KEY,
                    session_id VARCHAR(100),
                    question TEXT,
                    generated_sql TEXT,
                    rows_returned INTEGER,
                    execution_error TEXT,
                    latency_ms BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS execution_error TEXT");
        jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS rows_returned INTEGER");
        jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS latency_ms BIGINT");
    }

    // Ghi log cho mỗi lượt hỏi AI.
    public void log(String sessionId, String question, String sql, int rowsReturned, String error, long latencyMs) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_audit_logs
                        (session_id, question, generated_sql, rows_returned, execution_error, latency_ms)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    sessionId, question, sql, rowsReturned, error, latencyMs);
        } catch (Exception e) {
            log.warn("Failed to write AI audit log: {}", e.getMessage());
        }
    }
}
