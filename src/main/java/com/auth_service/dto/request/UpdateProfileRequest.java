package com.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Tên không được để trống")
    @Size(max = 120)
    String name,

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    String email
) {}
