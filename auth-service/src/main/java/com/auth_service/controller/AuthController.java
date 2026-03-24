package com.auth_service.controller;

import com.auth_service.dto.request.IntrospectRequest;
import com.auth_service.dto.request.LoginRequest;
import com.auth_service.dto.request.RefreshTokenRequest;
import com.auth_service.dto.request.RegisterRequest;
import com.auth_service.dto.response.IntrospectResponse;
import com.auth_service.dto.response.LoginResponse;
import com.auth_service.dto.response.RegisterResponse;
import com.auth_service.service.AuthService;
import com.common.api.ApiResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth APIs", description = "Đăng ký, đăng nhập và xác thực token")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Dang ky tai khoan")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Dang ky thanh cong", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Dang nhap")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Dang nhap thanh cong", authService.login(request));
    }

    @PostMapping("/introspect")
    @Operation(summary = "Kiem tra token noi bo")
    public ApiResponse<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request) {
        return ApiResponse.success("Kiem tra token thanh cong", authService.introspect(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Lay token moi bang refresh token")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("Lam moi token thanh cong", authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Dang xuat", security = {@SecurityRequirement(name = "BearerAuth")})
    public ApiResponse<String> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thieu Authorization header theo dinh dang Bearer <token>");
        }
        authService.logout(token);
        return ApiResponse.success("Dang xuat thanh cong", "OK");
    }

    private String extractToken(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String value = rawValue.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return value.substring(7).trim();
    }
}
