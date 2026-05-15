package com.auth_service.controller;

import com.auth_service.dto.request.CreateUserRequest;
import com.auth_service.dto.request.UpdateUserRoleRequest;
import com.auth_service.dto.response.UserResponse;
import com.auth_service.service.UserService;
import com.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Quản lý người dùng và phân quyền (Chỉ dành cho ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lấy danh sách người dùng")
    public ApiResponse<List<UserResponse>> getAll() {
        return ApiResponse.success("Lấy danh sách người dùng thành công", userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lấy thông tin chi tiết người dùng")
    public ApiResponse<UserResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy thông tin người dùng thành công", userService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Tạo người dùng mới")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("Tạo người dùng thành công", userService.createUser(request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Cập nhật vai trò người dùng")
    public ApiResponse<UserResponse> updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.success("Cập nhật vai trò thành công", userService.updateRoles(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Bật/Tắt trạng thái hoạt động của người dùng")
    public ApiResponse<UserResponse> toggleStatus(@PathVariable UUID id) {
        return ApiResponse.success("Cập nhật trạng thái thành công", userService.toggleStatus(id));
    }
}
