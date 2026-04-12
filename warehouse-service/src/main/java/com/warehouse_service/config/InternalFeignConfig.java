package com.warehouse_service.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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
        };
    }
}
