package com.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class OpenApiConfig {

        @Value("${app.openapi.server-url:}")
        private String openApiServerUrl;

        @Bean
        public OpenAPI warehouseOpenAPI() {
                String schemeName = "bearerAuth";
                OpenAPI openAPI = new OpenAPI()
                                .info(new Info()
                                                .title("Warehouse Management API")
                                                .version("1.0.0")
                                                .description("Modular monolith API for warehouse management"))
                                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                                .schemaRequirement(schemeName, new SecurityScheme()
                                                .name(schemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT"));

                if (StringUtils.hasText(openApiServerUrl)) {
                        openAPI.setServers(List.of(new Server().url(openApiServerUrl.trim())));
                }

                return openAPI;
        }
}
