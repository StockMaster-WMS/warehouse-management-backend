package com.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Ten dang nhap khong duoc de trong")
        @Size(min = 4, max = 50, message = "Ten dang nhap tu 4 den 50 ky tu")
        String username,

        @NotBlank(message = "Email khong duoc de trong")
        @Email(message = "Email khong hop le")
        @Size(max = 120, message = "Email khong duoc vuot qua 120 ky tu")
        String email,

        @NotBlank(message = "Mat khau khong duoc de trong")
        @Size(min = 8, max = 100, message = "Mat khau tu 8 den 100 ky tu")
        String password
) {
}
