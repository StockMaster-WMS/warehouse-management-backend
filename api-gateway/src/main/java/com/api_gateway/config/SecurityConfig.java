package com.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Value("${app.gateway.security.enabled:false}")
    private boolean gatewaySecurityEnabled;

    @Value("${app.gateway.security.jwt-secret}")
    private String jwtSecret;

    @Value("${app.gateway.cors.allowed-origin-patterns:${FRONTEND_ORIGIN:http://localhost:3000},http://127.0.0.1:3000}")
    private String allowedOriginPatterns;

    @Bean
    @Order(1)
    public SecurityWebFilterChain publicSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                "/api/auth/**",
                "/api/auth/v3/api-docs/**",
                "/api/products/v3/api-docs/**",
                "/api/warehouse/v3/api-docs/**",
                "/api/inbound/v3/api-docs/**",
                "/api/outbound/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/webjars/**",
                "/v3/api-docs/**",
                "/actuator/**"
            ))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
            .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        if (!gatewaySecurityEnabled) {
            http.authorizeExchange(exchange -> exchange.anyExchange().permitAll());
            return http.build();
        }

        http
            .authorizeExchange(exchange -> exchange
                // FIX: Cho phép toàn bộ OPTIONS request đi qua (preflight CORS)
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                // Public APIs (không cần Bearer) - dùng cho curl/test nhanh
                .pathMatchers(
                    // Auth
                    "/api/auth/**",
                    // Swagger docs của từng service
                    "/api/auth/v3/api-docs/**",
                    "/api/products/v3/api-docs/**",
                    "/api/warehouse/v3/api-docs/**",
                    "/api/inbound/v3/api-docs/**",
                    "/api/outbound/v3/api-docs/**",
                    // Swagger UI chung tại Gateway
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    // Actuator
                    "/actuator/**"
                ).permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKey key = new SecretKeySpec(
            jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"
        );
        return NimbusReactiveJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS384)
            .build();
    }
}
