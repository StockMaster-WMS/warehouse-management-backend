package com.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class SecurityConfigCorsTest {

    @Test
    void allowsProductionFrontendPreflightForRefresh() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig(null);
        ReflectionTestUtils.setField(securityConfig, "allowedOrigin", "");
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsFilter corsFilter = new CorsFilter(source);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/refresh");
        request.addHeader("Origin", "https://warehouse.ryon.website");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        corsFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                fail("Preflight request should be handled by the CORS filter"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://warehouse.ryon.website");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("POST");
    }

    @Test
    void allowsRyonSubdomainPatterns() {
        SecurityConfig securityConfig = new SecurityConfig(null);
        ReflectionTestUtils.setField(securityConfig, "allowedOrigin", "");
        var corsConfiguration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/refresh"));

        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.checkOrigin("https://warehouse.ryon.website"))
                .isEqualTo("https://warehouse.ryon.website");
        assertThat(corsConfiguration.checkOrigin("https://apiwarehouse.ryon.website"))
                .isEqualTo("https://apiwarehouse.ryon.website");
    }
}
