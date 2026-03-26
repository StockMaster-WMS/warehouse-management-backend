package com.outbound_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${gateway.url:http://localhost:9000}")
    private String gatewayUrl;

    @Bean
    public OpenAPI openAPI() {
        String base = gatewayUrl == null || gatewayUrl.isBlank()
                ? "http://localhost:9000"
                : gatewayUrl.replaceAll("/+$", "");
        return new OpenAPI()
            .info(new Info()
                .title("Outbound Service API")
                .version("1.0"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            // "/" trước: Try it out từ Swagger trên Gateway luôn cùng origin (tránh CORS localhost vs 127.0.0.1)
            .servers(List.of(
                new Server().url("/").description("Gateway — cùng host với Swagger UI"),
                new Server().url(base).description("API Gateway (URL tuyệt đối)")));
    }
}
