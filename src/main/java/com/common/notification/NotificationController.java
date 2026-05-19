package com.common.notification;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@Tag(name = "Thông báo", description = "Quản lý thông báo trong hệ thống")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách thông báo của người dùng hiện tại")
    public ApiResponse<PagedResponse<NotificationResponse>> getMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Lấy danh sách thông báo thành công",
                notificationService.findMine(currentUserId(authentication), unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đếm số thông báo chưa đọc")
    public ApiResponse<Map<String, Long>> unreadCount(Authentication authentication) {
        long count = notificationService.countUnread(currentUserId(authentication));
        return ApiResponse.success("Lấy số thông báo chưa đọc thành công", Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ApiResponse<NotificationResponse> markAsRead(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success("Đánh dấu thông báo đã đọc thành công",
                notificationService.markAsRead(currentUserId(authentication), id));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc")
    public ApiResponse<Map<String, Integer>> markAllAsRead(Authentication authentication) {
        int updated = notificationService.markAllAsRead(currentUserId(authentication));
        return ApiResponse.success("Đánh dấu tất cả thông báo đã đọc thành công", Map.of("updated", updated));
    }

    private static UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chua dang nhap");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.FORBIDDEN, "Token không hợp lệ");
        }
    }
}
