package com.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String mode,
        Jwt jwt
) {
    public boolean isPublicMode() {
        return mode == null || mode.equalsIgnoreCase("public");
    }

    public record Jwt(
            String issuer,
            String secret,
            long accessTokenExpirationSeconds
    ) {
    }
}
