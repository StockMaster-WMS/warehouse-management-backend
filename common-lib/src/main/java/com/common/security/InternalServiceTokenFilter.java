package com.common.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    @Value("${internal.security.enabled:${INTERNAL_SECURITY_ENABLED:false}}")
    private boolean enabled;

    @Value("${internal.security.header-name:${INTERNAL_SECURITY_HEADER_NAME:X-Internal-Service-Token}}")
    private String headerName;

    @Value("${internal.service-token:${INTERNAL_SERVICE_TOKEN:}}")
    private String token;

    @PostConstruct
    void validateConfiguration() {
        if (enabled && !StringUtils.hasText(token)) {
            throw new IllegalStateException("internal.security.token must be configured when internal security is enabled");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || isPublicActuatorPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String providedToken = request.getHeader(headerName);
        if (!token.equals(providedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid internal service token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicActuatorPath(String path) {
        return path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info");
    }
}
