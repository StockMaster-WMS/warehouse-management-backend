package com.auth_service.dto.response;

public record UserStatisticsResponse(
        long totalUsers,
        long activeUsers,
        long inactiveUsers,
        long adminUsers
) {
}
