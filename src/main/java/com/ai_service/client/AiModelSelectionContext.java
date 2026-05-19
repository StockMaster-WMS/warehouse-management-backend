package com.ai_service.client;

import org.springframework.util.StringUtils;

public final class AiModelSelectionContext {

    private static final ThreadLocal<Selection> CURRENT = new ThreadLocal<>();

    private AiModelSelectionContext() {
    }

    public static void set(String provider, String model) {
        CURRENT.set(new Selection(clean(provider), clean(model)));
    }

    public static String provider() {
        Selection selection = CURRENT.get();
        return selection == null ? null : selection.provider();
    }

    public static String model() {
        Selection selection = CURRENT.get();
        return selection == null ? null : selection.model();
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)
                ? null
                : trimmed;
    }

    private record Selection(String provider, String model) {
    }
}
