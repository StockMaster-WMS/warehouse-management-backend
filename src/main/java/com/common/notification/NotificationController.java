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
@Tag(name = "Notification", description = "Quan ly thong bao trong he thong")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lay danh sach thong bao cua nguoi dung hien tai")
    public ApiResponse<PagedResponse<NotificationResponse>> getMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Lay danh sach thong bao thanh cong",
                notificationService.findMine(currentUserId(authentication), unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Dem so thong bao chua doc")
    public ApiResponse<Map<String, Long>> unreadCount(Authentication authentication) {
        long count = notificationService.countUnread(currentUserId(authentication));
        return ApiResponse.success("Lay so thong bao chua doc thanh cong", Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh dau mot thong bao da doc")
    public ApiResponse<NotificationResponse> markAsRead(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.success("Danh dau thong bao da doc thanh cong",
                notificationService.markAsRead(currentUserId(authentication), id));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh dau tat ca thong bao da doc")
    public ApiResponse<Map<String, Integer>> markAllAsRead(Authentication authentication) {
        int updated = notificationService.markAllAsRead(currentUserId(authentication));
        return ApiResponse.success("Danh dau tat ca thong bao da doc thanh cong", Map.of("updated", updated));
    }

    private static UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chua dang nhap");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.FORBIDDEN, "Token khong hop le");
        }
    }
}
