package com.auth_service.security;

import com.auth_service.config.AuthProperties;
import com.auth_service.entity.UserAccount;
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

    private final AuthProperties authProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(authProperties.jwt().accessTokenExpirationSeconds());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .issuer(authProperties.jwt().issuer())
                .subject(user.getId().toString())
                .id(jti)
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles())
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
        String jti = UUID.randomUUID().toString();

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
