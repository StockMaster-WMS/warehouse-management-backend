package com.auth_service.security;

import com.auth_service.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderSecurityTest {

    @Test
    void rejectsDefaultPlaceholderSecret() {
        AuthProperties properties = new AuthProperties(
                "public",
                new AuthProperties.Jwt(
                        "stockmaster",
                        "change-me-to-a-very-long-secret-key-at-least-32-bytes",
                        900,
                        604800));

        assertThatThrownBy(() -> new JwtTokenProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default placeholder");
    }

    @Test
    void acceptsStrongSecret() {
        AuthProperties properties = new AuthProperties(
                "public",
                new AuthProperties.Jwt(
                        "stockmaster",
                        "0123456789abcdef0123456789abcdef",
                        900,
                        604800));

        assertThatCode(() -> new JwtTokenProvider(properties)).doesNotThrowAnyException();
    }
}
