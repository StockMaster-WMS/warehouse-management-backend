package com.inbound_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.inbound_service", "com.common"})
@EnableFeignClients(basePackages = "com.inbound_service.client")
@EntityScan(basePackages = {"com.inbound_service", "com.common"})
@EnableJpaRepositories(basePackages = {"com.inbound_service", "com.common"})
public class InboundServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InboundServiceApplication.class, args);
	}
}
