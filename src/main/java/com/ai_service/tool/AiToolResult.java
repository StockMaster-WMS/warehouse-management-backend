package com.ai_service.tool;

import java.util.List;
import java.util.Map;

public record AiToolResult(
        String toolName,
        Object data,
        String message,
        boolean dataBacked,
        List<String> dataSources,
        List<String> missingParams,
        Map<String, Object> uiMetadata
) {
    public AiToolResult(String toolName, Object data, String message, boolean dataBacked) {
        this(toolName, data, message, dataBacked, List.of(), List.of(), Map.of());
    }

    // Tạo kết quả tool có dữ liệu từ DB.
    public static AiToolResult data(String toolName, Object data) {
        return new AiToolResult(toolName, data, null, true);
    }

    public static AiToolResult data(String toolName, Object data, List<String> dataSources) {
        return new AiToolResult(toolName, data, null, true, dataSources, List.of(), Map.of());
    }

    // Tạo kết quả tool chỉ có thông báo cố định.
    public static AiToolResult message(String toolName, String message) {
        return new AiToolResult(toolName, null, message, false);
    }

    public AiToolResult withMetadata(List<String> sources, List<String> missing) {
        return new AiToolResult(toolName, data, message, dataBacked,
                sources == null ? List.of() : sources,
                missing == null ? List.of() : missing,
                uiMetadata == null ? Map.of() : uiMetadata);
    }

    public AiToolResult withUiMetadata(Map<String, Object> metadata) {
        return new AiToolResult(toolName, data, message, dataBacked,
                dataSources == null ? List.of() : dataSources,
                missingParams == null ? List.of() : missingParams,
                metadata == null ? Map.of() : metadata);
    }
}
