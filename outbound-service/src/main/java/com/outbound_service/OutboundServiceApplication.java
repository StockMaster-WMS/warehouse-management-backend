package com.outbound_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.outbound_service", "com.common"})
@EnableFeignClients
public class OutboundServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutboundServiceApplication.class, args);
	}
}
