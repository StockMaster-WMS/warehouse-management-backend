package com.common.api;

import java.time.Instant;
import java.util.Map;

public class ApiErrorResponse {

    private final boolean success;
    private final String errorCode;
    private final String message;
    private final String path;
    private final Instant timestamp;
    private final Map<String, String> validationErrors;

    public ApiErrorResponse(String errorCode, String message, String path, Map<String, String> validationErrors) {
        this.success = false;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
        this.timestamp = Instant.now();
        this.validationErrors = validationErrors;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}
