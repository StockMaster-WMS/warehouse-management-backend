package com.ai_service.tool;

import java.util.List;

public record AiToolResult(
        String toolName,
        Object data,
        String message,
        boolean dataBacked,
        List<String> dataSources,
        List<String> missingParams
) {
    public AiToolResult(String toolName, Object data, String message, boolean dataBacked) {
        this(toolName, data, message, dataBacked, List.of(), List.of());
    }

    // Tạo kết quả tool có dữ liệu từ DB.
    public static AiToolResult data(String toolName, Object data) {
        return new AiToolResult(toolName, data, null, true);
    }

    public static AiToolResult data(String toolName, Object data, List<String> dataSources) {
        return new AiToolResult(toolName, data, null, true, dataSources, List.of());
    }

    // Tạo kết quả tool chỉ có thông báo cố định.
    public static AiToolResult message(String toolName, String message) {
        return new AiToolResult(toolName, null, message, false);
    }

    public AiToolResult withMetadata(List<String> sources, List<String> missing) {
        return new AiToolResult(toolName, data, message, dataBacked,
                sources == null ? List.of() : sources,
                missing == null ? List.of() : missing);
    }
}
