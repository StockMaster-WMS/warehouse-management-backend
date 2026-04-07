package com.auth_service.service;

import com.auth_service.dto.request.IntrospectRequest;
import com.auth_service.dto.request.LoginRequest;
import com.auth_service.dto.request.RegisterRequest;
import com.auth_service.dto.response.IntrospectResponse;
import com.auth_service.dto.response.RegisterResponse;
import com.auth_service.entity.TokenBlacklist;
import com.auth_service.entity.UserAccount;
import com.auth_service.repository.RoleRepository;
import com.auth_service.repository.TokenBlacklistRepository;
import com.auth_service.repository.UserRepository;
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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String DEFAULT_ROLE_CODE = "USER";

    private final UserRepository userAccountRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // Đăng ký tài khoản mới và gán vai trò mặc định.
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên đăng nhập đã tồn tại");
        }
        if (userAccountRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
        }

        var defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Vai trò mặc định chưa được khởi tạo"));

        var assignedRoles = new LinkedHashSet<com.auth_service.entity.Role>();
        assignedRoles.add(defaultRole);

        UserAccount user = UserAccount.builder()
                .username(request.username().trim())
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
            .roles(assignedRoles)
                .build();

        UserAccount saved = userAccountRepository.save(user);
        return new RegisterResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
            saved.getRoleCodesCsv(),
                saved.getCreatedAt());
    }

    // Đăng nhập và phát hành access/refresh token.
    @Transactional(readOnly = true)
    public AuthTokens login(LoginRequest request) {
        String credential = request.username().trim();
        UserAccount user = userAccountRepository.findByUsernameOrEmail(credential, credential)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Thông tin đăng nhập không hợp lệ"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khóa");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thông tin đăng nhập không hợp lệ");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        return toAuthTokens(user, accessToken, refreshToken);
    }

    // Kiểm tra tính hợp lệ của token và trả thông tin claims.
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

    // Làm mới phiên bằng refresh token hợp lệ.
    @Transactional
    public AuthTokens refresh(String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu refresh token");
            }

            // Chỉ chấp nhận đúng refresh token
            if (!"REFRESH".equals(jwtTokenProvider.getTokenType(refreshToken))) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Token không phải refresh token");
            }

            // Kiểm tra xem token đã bị blacklist hay chưa
            String jti = jwtTokenProvider.getJti(refreshToken);
            if (tokenBlacklistRepository.existsByTokenJti(jti)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Refresh token không còn hiệu lực");
            }

            // Lấy người dùng từ token
            var userId = jwtTokenProvider.getUserId(refreshToken);
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Người dùng không tồn tại"));

            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khóa");
            }

            // Xoay vòng token: vô hiệu hóa refresh token cũ và phát hành cặp token mới
            blacklistToken(refreshToken);
            String newAccessToken = jwtTokenProvider.generateAccessToken(user);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
            return toAuthTokens(user, newAccessToken, newRefreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Refresh token không hợp lệ");
        }
    }

    // Đăng xuất bằng cách đưa token vào blacklist.
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            blacklistByType(accessToken, "ACCESS");
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            blacklistByType(refreshToken, "REFRESH");
        }
    }

    // Lưu token vào blacklist theo cơ chế idempotent.
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

    private void blacklistByType(String token, String expectedType) {
        try {
            String tokenType = jwtTokenProvider.getTokenType(token);
            if (!Objects.equals(expectedType, tokenType)) {
                return;
            }
            blacklistToken(token);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Logout vẫn trả thành công ngay cả khi token lỗi hoặc hết hạn.
        }
    }

    private AuthTokens toAuthTokens(UserAccount user, String accessToken, String refreshToken) {
        return new AuthTokens(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoleCodesCsv());
    }

    public long getRefreshTokenExpirationSeconds() {
        return jwtTokenProvider.getRefreshTokenExpirationSeconds();
    }

    public record AuthTokens(
            String accessToken,
            String refreshToken,
            UUID userId,
            String username,
            String email,
            String roles
    ) {
    }
}
