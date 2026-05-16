package com;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

class DatabaseExportRunnerTest {

    // Xuất toàn bộ dữ liệu từng bảng trong schema public ra một file JSON.
    @Test
    void exportAllPublicTablesToJson() throws Exception {
        Map<String, String> env = loadEnv(Path.of(".env"));
        String url = required(env, "WAREHOUSE_DB_URL");
        String username = required(env, "WAREHOUSE_DB_USERNAME");
        String password = required(env, "WAREHOUSE_DB_PASSWORD");

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("generatedAt", OffsetDateTime.now().toString());
        export.put("database", redactJdbcUrl(url));

        Map<String, Object> tables = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            for (String tableName : getPublicTables(connection)) {
                Map<String, Object> table = new LinkedHashMap<>();
                List<Map<String, Object>> rows = readTable(connection, tableName);
                table.put("rowCount", rows.size());
                table.put("rows", rows);
                tables.put(tableName, table);
            }
        }

        export.put("tableCount", tables.size());
        export.put("tables", tables);

        Path outputDir = Path.of("database-export");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("all_public_tables.json");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile.toFile(), export);
        System.out.println("Database export written to " + outputFile.toAbsolutePath());
    }

    // Lấy danh sách bảng thường trong schema public.
    private List<String> getPublicTables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_type = 'BASE TABLE'
                     ORDER BY table_name
                     """)) {
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    // Đọc toàn bộ dòng của một bảng thành danh sách map.
    private List<Map<String, Object>> readTable(Connection connection, String tableName) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT * FROM " + quoteIdentifier(tableName);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), normalizeValue(rs.getObject(i)));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    // Chuyển value JDBC sang dạng JSON ghi được.
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof java.sql.Date
                || value instanceof java.sql.Time
                || value instanceof java.sql.Timestamp
                || value.getClass().getName().startsWith("java.time.")
                || value.getClass().getName().startsWith("java.util.UUID")) {
            return value.toString();
        }
        return value;
    }

    // Đọc file .env đơn giản theo dạng KEY=VALUE.
    private Map<String, String> loadEnv(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            int index = trimmed.indexOf('=');
            String key = trimmed.substring(0, index).trim();
            String value = trimmed.substring(index + 1).trim();
            values.put(key, stripQuotes(value));
        }
        return values;
    }

    // Lấy biến bắt buộc từ .env.
    private String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + key + " in .env");
        }
        return value;
    }

    // Bỏ dấu quote ngoài cùng nếu có.
    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    // Quote tên bảng để tránh lỗi với ký tự đặc biệt.
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // Ẩn thông tin nhạy cảm trong JDBC URL.
    private String redactJdbcUrl(String url) {
        return url.replaceAll("(?i)(password=)[^&]+", "$1***");
    }
}
