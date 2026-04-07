package com.auth_service;

import com.auth_service.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.auth_service", "com.common"})
@EnableConfigurationProperties(AuthProperties.class)
@EnableScheduling
@EntityScan(basePackages = {"com.auth_service", "com.common"})
@EnableJpaRepositories(basePackages = {"com.auth_service", "com.common"})
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
