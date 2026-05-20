package com.auth_service.dto.response;

import com.common.audit.AuditLogResponse;

import java.util.List;
import java.util.Map;

public record UserDetailResponse(
        UserResponse user,
        Map<String, Object> statistics,
        List<AuditLogResponse> recentAuditLogs
) {
}
