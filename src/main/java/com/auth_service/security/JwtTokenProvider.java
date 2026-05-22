package com.auth_service.security;

import com.auth_service.config.AuthProperties;
import com.auth_service.entity.UserAccount;
import com.common.util.UuidUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String DEFAULT_WEAK_SECRET = "change-me-to-a-very-long-secret-key-at-least-32-bytes";
    private static final int MIN_SECRET_LENGTH = 32;

    private final AuthProperties authProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
        String secret = authProperties.jwt().secret();
        validateSecret(secret);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("AUTH_JWT_SECRET is required");
        }
        if (DEFAULT_WEAK_SECRET.equals(secret)) {
            throw new IllegalStateException("AUTH_JWT_SECRET must not use the default placeholder value");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("AUTH_JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters");
        }
    }

    public String generateAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(authProperties.jwt().accessTokenExpirationSeconds());
        String jti = UuidUtils.uuidV7().toString();

        return Jwts.builder()
                .issuer(authProperties.jwt().issuer())
                .subject(user.getId().toString())
                .id(jti)
                .claim("username", user.getUsername())
            .claim("roles", user.getRoleCodesCsv())
                .claim("token_type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UserAccount user) {
        Instant now = Instant.now();
        // Refresh token có expiration dài hơn: 7 ngày
        Instant expiresAt = now.plusSeconds(7 * 24 * 60 * 60);
        String jti = UuidUtils.uuidV7().toString();

        return Jwts.builder()
                .issuer(authProperties.jwt().issuer())
                .subject(user.getId().toString())
                .id(jti)
                .claim("username", user.getUsername())
                .claim("token_type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String getUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String getRoles(String token) {
        return parseClaims(token).get("roles", String.class);
    }

    public String getTokenType(String token) {
        return parseClaims(token).get("token_type", String.class);
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public long getAccessTokenExpirationSeconds() {
        return authProperties.jwt().accessTokenExpirationSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return 7 * 24 * 60 * 60; // 7 ngày
    }
}
