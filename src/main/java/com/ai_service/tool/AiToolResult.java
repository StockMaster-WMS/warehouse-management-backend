package com.ai_service.tool;

public record AiToolResult(
        String toolName,
        Object data,
        String message,
        boolean dataBacked
) {
    public static AiToolResult data(String toolName, Object data) {
        return new AiToolResult(toolName, data, null, true);
    }

    public static AiToolResult message(String toolName, String message) {
        return new AiToolResult(toolName, null, message, false);
    }
}
