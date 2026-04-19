package com.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI warehouseOpenAPI() {
                String schemeName = "bearerAuth";
                return new OpenAPI()
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
        }
}
