package com.auth_service.controller;

import com.auth_service.dto.request.IntrospectRequest;
import com.auth_service.dto.request.LoginRequest;
import com.auth_service.dto.request.UpdateProfileRequest;
import com.auth_service.dto.request.ChangePasswordRequest;
import com.auth_service.dto.response.LoginResponse;
import com.auth_service.service.AuthService;
import com.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth ", description = "Đăng ký, đăng nhập và xác thực token")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/";

    private final AuthService authService;

    @Value("${auth.cookie.secure:auto}")
    private String refreshCookieSecure;

    @Value("${auth.cookie.same-site:auto}")
    private String refreshCookieSameSite;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthService.AuthTokens tokens = authService.login(request);
        addRefreshCookie(httpRequest, response, tokens.refreshToken());
        return ApiResponse.success("Đăng nhập thành công", toLoginResponse(tokens));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng hiện tại", security = { @SecurityRequirement(name = "bearerAuth") })
    public ApiResponse<LoginResponse.UserInfo> me(
            HttpServletRequest request) {
        return ApiResponse.success(
                "Lấy thông tin người dùng hiện tại thành công",
                authService.me(extractToken(request.getHeader(HttpHeaders.AUTHORIZATION))));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Lấy token mới bằng refresh token")
    public ApiResponse<LoginResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractCookieValue(request, REFRESH_COOKIE_NAME);
        AuthService.AuthTokens tokens = authService.refresh(refreshToken);
        addRefreshCookie(request, response, tokens.refreshToken());
        return ApiResponse.success("Làm mới token thành công", toLoginResponse(tokens));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", security = { @SecurityRequirement(name = "bearerAuth") })
    public ApiResponse<String> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = extractToken(authHeader);
        String refreshToken = extractCookieValue(request, REFRESH_COOKIE_NAME);
        authService.logout(accessToken, refreshToken);
        clearRefreshCookie(request, response);
        return ApiResponse.success("Đăng xuất thành công", "OK");
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật hồ sơ cá nhân", security = { @SecurityRequirement(name = "bearerAuth") })
    public ApiResponse<LoginResponse.UserInfo> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = authService.introspect(new IntrospectRequest(extractToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)))).userId();
        return ApiResponse.success("Cập nhật hồ sơ thành công", authService.updateProfile(userId, request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu", security = { @SecurityRequirement(name = "bearerAuth") })
    public ApiResponse<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = authService.introspect(new IntrospectRequest(extractToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)))).userId();
        authService.changePassword(userId, request);
        return ApiResponse.success("Đổi mật khẩu thành công", "OK");
    }

    private LoginResponse toLoginResponse(AuthService.AuthTokens tokens) {
        return new LoginResponse(
                tokens.accessToken(),
                new LoginResponse.UserInfo(
                        tokens.userId(),
                        tokens.username(),
                        tokens.email(),
                        tokens.fullName(),
                        tokens.roles()));
    }

    private void addRefreshCookie(HttpServletRequest request, HttpServletResponse response, String refreshToken) {
        boolean secure = resolveRefreshCookieSecure(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(resolveRefreshCookieSameSite(secure))
                .path(REFRESH_COOKIE_PATH)
                .maxAge(authService.getRefreshTokenExpirationSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = resolveRefreshCookieSecure(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(resolveRefreshCookieSameSite(secure))
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean resolveRefreshCookieSecure(HttpServletRequest request) {
        if (refreshCookieSecure != null && !refreshCookieSecure.isBlank()
                && !"auto".equalsIgnoreCase(refreshCookieSecure.trim())) {
            return Boolean.parseBoolean(refreshCookieSecure.trim());
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedSsl = request.getHeader("X-Forwarded-Ssl");
        String forwarded = request.getHeader("Forwarded");
        return request.isSecure()
                || containsHeaderValue(forwardedProto, "https")
                || "on".equalsIgnoreCase(forwardedSsl)
                || forwardedHasHttpsProto(forwarded)
                || isHttpsUrl(request.getHeader("Origin"))
                || isHttpsUrl(request.getHeader("Referer"));
    }

    private boolean containsHeaderValue(String rawValue, String expectedValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return false;
        }

        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .anyMatch(value -> expectedValue.equalsIgnoreCase(value));
    }

    private boolean forwardedHasHttpsProto(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) {
            return false;
        }

        return Arrays.stream(forwarded.split("[,;]"))
                .map(String::trim)
                .anyMatch(part -> part.equalsIgnoreCase("proto=https"));
    }

    private boolean isHttpsUrl(String rawUrl) {
        return rawUrl != null && rawUrl.regionMatches(true, 0, "https://", 0, 8);
    }

    private String resolveRefreshCookieSameSite(boolean secure) {
        if (refreshCookieSameSite != null && !refreshCookieSameSite.isBlank()
                && !"auto".equalsIgnoreCase(refreshCookieSameSite.trim())) {
            return refreshCookieSameSite.trim();
        }

        return secure ? "None" : "Lax";
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
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
