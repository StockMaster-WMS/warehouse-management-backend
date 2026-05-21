package com.ai_putway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AIConfig {

    @Value("${ai.engine.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${ai.engine.read-timeout:15000}")
    private int readTimeout;

    @Bean(name = "aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .readTimeout(Duration.ofMillis(readTimeout))
                .build();
    }
}
