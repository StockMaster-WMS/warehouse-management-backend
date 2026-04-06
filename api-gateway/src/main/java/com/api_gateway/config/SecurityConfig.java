package com.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${app.gateway.security.enabled:false}")
    private boolean gatewaySecurityEnabled;

    @Value("${app.gateway.security.jwt-secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            // FIX: Trỏ rõ tới corsConfigurationSource bean thay vì withDefaults()
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
                .pathMatchers(HttpMethod.GET,
                    "/api/**"
                ).permitAll()
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
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        // Bật dòng dưới nếu bạn cần gửi cookie/credentials
        // config.setAllowCredentials(true);

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
            .macAlgorithm(MacAlgorithm.HS512)
            .build();
    }
}