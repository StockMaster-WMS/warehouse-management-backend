package com.inbound_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.inbound_service", "com.common"})
@EnableFeignClients(basePackages = "com.inbound_service.client")
public class InboundServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InboundServiceApplication.class, args);
	}

}
