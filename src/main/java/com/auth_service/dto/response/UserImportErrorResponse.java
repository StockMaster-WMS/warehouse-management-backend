package com.auth_service.dto.response;

public record UserImportErrorResponse(
        int rowNumber,
        String username,
        String email,
        String message
) {
}
