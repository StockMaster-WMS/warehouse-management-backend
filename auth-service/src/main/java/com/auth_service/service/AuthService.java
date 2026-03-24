package com.auth_service.service;

import com.auth_service.dto.request.IntrospectRequest;
import com.auth_service.dto.request.LoginRequest;
import com.auth_service.dto.request.RefreshTokenRequest;
import com.auth_service.dto.request.RegisterRequest;
import com.auth_service.dto.response.IntrospectResponse;
import com.auth_service.dto.response.LoginResponse;
import com.auth_service.dto.response.RegisterResponse;
import com.auth_service.entity.TokenBlacklist;
import com.auth_service.entity.UserAccount;
import com.auth_service.repository.TokenBlacklistRepository;
import com.auth_service.repository.UserAccountRepository;
import com.auth_service.security.JwtTokenProvider;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên đăng nhập đã tồn tại");
        }
        if (userAccountRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
        }

        UserAccount user = UserAccount.builder()
                .username(request.username().trim())
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles("USER")
                .build();

        UserAccount saved = userAccountRepository.save(user);
        return new RegisterResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRoles(),
                saved.getCreatedAt());
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Thông tin đăng nhập không hợp lệ"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khóa");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thông tin đăng nhập không hợp lệ");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                jwtTokenProvider.getRefreshTokenExpirationSeconds(),
                user.getUsername(),
                user.getRoles());
    }

    public IntrospectResponse introspect(IntrospectRequest request) {
        try {
            var claims = jwtTokenProvider.parseClaims(request.token());

            if (tokenBlacklistRepository.existsByTokenJti(claims.getId())) {
                return IntrospectResponse.invalid();
            }

            return new IntrospectResponse(
                    true,
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("roles", String.class),
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            return IntrospectResponse.invalid();
        }
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        try {
            String refreshToken = request.refreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu refresh token");
            }

            // Chi chap nhan dung refresh token
            if (!"REFRESH".equals(jwtTokenProvider.getTokenType(refreshToken))) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Token không phải refresh token");
            }

            // Kiểm tra xem token đã bị blacklist hay chưa
            String jti = jwtTokenProvider.getJti(refreshToken);
            if (tokenBlacklistRepository.existsByTokenJti(jti)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Refresh token không còn hiệu lực");
            }

            // Lấy user từ token
            var userId = jwtTokenProvider.getUserId(refreshToken);
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Người dùng không tồn tại"));

            if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khóa");
        }

            // Rotation: vô hiệu hóa refresh token cũ và phát hành cặp token mới
            blacklistToken(refreshToken);
            String newAccessToken = jwtTokenProvider.generateAccessToken(user);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
            return new LoginResponse(
                    newAccessToken,
                    newRefreshToken,
                    "Bearer",
                    jwtTokenProvider.getAccessTokenExpirationSeconds(),
                    jwtTokenProvider.getRefreshTokenExpirationSeconds(),
                    user.getUsername(),
                    user.getRoles());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Refresh token không hợp lệ");
        }
    }

    @Transactional
    public void logout(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu token đăng xuất");
            }

            String tokenType = jwtTokenProvider.getTokenType(token);
            if (!"ACCESS".equals(tokenType) && !"REFRESH".equals(tokenType)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Loại token không hợp lệ");
            }

            blacklistToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Token không hợp lệ");
        }
    }

    private void blacklistToken(String token) {
        var userId = jwtTokenProvider.getUserId(token);
        String jti = jwtTokenProvider.getJti(token);
        var expiration = jwtTokenProvider.getExpiration(token);

        TokenBlacklist blacklistedToken = TokenBlacklist.builder()
                .userId(userId)
                .tokenJti(jti)
                .expiresAt(OffsetDateTime.ofInstant(expiration, ZoneOffset.UTC))
                .build();

        try {
            tokenBlacklistRepository.save(blacklistedToken);
        } catch (DataIntegrityViolationException ex) {
            // Idempotent: token đã tồn tại trong blacklist thì bỏ qua
        }
    }
}
