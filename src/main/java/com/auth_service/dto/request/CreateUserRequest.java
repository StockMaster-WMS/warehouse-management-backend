package com.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
    @NotBlank(message = "Username không được để trống")
    @Size(min = 3, max = 50)
    String username,

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    String email,

    @NotBlank(message = "Họ tên không được để trống")
    String fullName,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6)
    String password,

    @NotEmpty(message = "Cần gán ít nhất một vai trò")
    Set<String> roles
) {}
