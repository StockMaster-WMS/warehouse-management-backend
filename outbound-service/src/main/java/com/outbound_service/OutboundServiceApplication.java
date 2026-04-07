package com.outbound_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.outbound_service", "com.common"})
@EnableFeignClients
@EntityScan(basePackages = {"com.outbound_service", "com.common"})
@EnableJpaRepositories(basePackages = {"com.outbound_service", "com.common"})
public class OutboundServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutboundServiceApplication.class, args);
	}
}
