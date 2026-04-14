package com.inbound_service.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class InternalFeignConfig {

    @Value("${internal.security.header-name:${INTERNAL_SECURITY_HEADER_NAME:X-Internal-Service-Token}}")
    private String headerName;

    @Value("${internal.service-token:${INTERNAL_SERVICE_TOKEN:}}")
    private String token;

    @Bean
    public RequestInterceptor internalServiceTokenRequestInterceptor() {
        return template -> {
            if (StringUtils.hasText(token)) {
                template.header(headerName, token);
            }
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                String authorization = request.getHeader("Authorization");
                if (StringUtils.hasText(authorization)) {
                    template.header("Authorization", authorization);
                }
            }
        };
    }
}
