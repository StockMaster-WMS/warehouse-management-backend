package com.auth_service.dto.response;

import java.util.List;

public record UserImportResultResponse(
        int totalRows,
        int successCount,
        int failedCount,
        List<UserResponse> users,
        List<UserImportErrorResponse> errors
) {
}
