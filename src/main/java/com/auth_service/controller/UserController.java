package com.auth_service.controller;

import com.auth_service.dto.request.AdminResetPasswordRequest;
import com.auth_service.dto.request.CreateUserRequest;
import com.auth_service.dto.request.UpdateUserRequest;
import com.auth_service.dto.request.UpdateUserRoleRequest;
import com.auth_service.dto.response.RoleResponse;
import com.auth_service.dto.response.UserDetailResponse;
import com.auth_service.dto.response.UserImportResultResponse;
import com.auth_service.dto.response.UserResponse;
import com.auth_service.dto.response.UserStatisticsResponse;
import com.auth_service.service.UserService;
import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Quản lý người dùng và phân quyền")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Danh sách người dùng", description = "Có search, lọc role/trạng thái và phân trang")
    public ApiResponse<PagedResponse<UserResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection(sortDir), normalizeSort(sort)));
        return ApiResponse.success("Lấy danh sách người dùng thành công",
                userService.findUsers(pageable, keyword, role, active));
    }

    @GetMapping("/warehouse-staff")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Danh sách nhân viên kho đang hoạt động", description = "Dùng cho các màn điều phối nhiệm vụ kho như picking và putaway")
    public ApiResponse<List<UserResponse>> getActiveWarehouseStaff(@RequestParam(required = false) UUID warehouseId,
            Authentication authentication) {
        return ApiResponse.success("Lấy danh sách nhân viên kho thành công",
                userService.getActiveWarehouseStaff(warehouseId, warehouseAccessService.visibleWarehouseIds(authentication)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Thống kê người dùng")
    public ApiResponse<UserStatisticsResponse> getStatistics() {
        return ApiResponse.success("Lấy thống kê người dùng thành công", userService.getStatistics());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Danh sách vai trò")
    public ApiResponse<List<RoleResponse>> getRoles() {
        return ApiResponse.success("Lấy danh sách vai trò thành công", userService.getRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lấy thông tin người dùng")
    public ApiResponse<UserResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy thông tin người dùng thành công", userService.getUserById(id));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Chi tiết người dùng kèm thống kê và lịch sử")
    public ApiResponse<UserDetailResponse> getDetail(@PathVariable UUID id) {
        return ApiResponse.success("Lấy chi tiết người dùng thành công", userService.getUserDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Tạo người dùng mới")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("Tạo người dùng thành công", userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Cập nhật người dùng")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return ApiResponse.success("Cập nhật người dùng thành công",
                userService.updateUser(id, request, currentUserId(authentication)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Cập nhật vai trò người dùng")
    public ApiResponse<UserResponse> updateRoles(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            Authentication authentication) {
        return ApiResponse.success("Cập nhật vai trò thành công",
                userService.updateRoles(id, request, currentUserId(authentication)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Bật/tắt trạng thái hoạt động của người dùng")
    public ApiResponse<UserResponse> toggleStatus(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Cập nhật trạng thái thành công",
                userService.toggleStatus(id, currentUserId(authentication)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Admin đặt lại mật khẩu người dùng")
    public ApiResponse<UserResponse> resetPassword(@PathVariable UUID id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        return ApiResponse.success("Đặt lại mật khẩu thành công", userService.resetPassword(id, request));
    }

    @PostMapping(value = "/import/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Preview import người dùng từ Excel")
    public ApiResponse<UserImportResultResponse> previewImport(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Preview import người dùng thành công", userService.previewImport(file));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Import người dùng từ Excel")
    public ApiResponse<UserImportResultResponse> importUsers(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Import người dùng thành công", userService.importUsers(file));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }

    private Sort.Direction sortDirection(String sortDir) {
        try {
            return Sort.Direction.fromString(sortDir);
        } catch (IllegalArgumentException ex) {
            return Sort.Direction.ASC;
        }
    }

    private String normalizeSort(String sort) {
        String value = sort == null ? "" : sort.trim();
        Map<String, String> aliases = Map.of(
                "name", "fullName",
                "full_name", "fullName",
                "status", "isActive",
                "active", "isActive");
        String normalized = aliases.getOrDefault(value, value);
        Set<String> allowed = Set.of("username", "email", "fullName", "isActive", "createdAt");
        return allowed.contains(normalized) ? normalized : "createdAt";
    }
}
