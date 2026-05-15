package com.ai_service.tool;

public record AiToolResult(
        String toolName,
        Object data,
        String message,
        boolean dataBacked
) {
    // Tạo kết quả tool có dữ liệu từ DB.
    public static AiToolResult data(String toolName, Object data) {
        return new AiToolResult(toolName, data, null, true);
    }

    // Tạo kết quả tool chỉ có thông báo cố định.
    public static AiToolResult message(String toolName, String message) {
        return new AiToolResult(toolName, null, message, false);
    }
}
