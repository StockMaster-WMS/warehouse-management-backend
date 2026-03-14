package com.inbound_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.inbound_service", "com.common"})
public class InboundServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InboundServiceApplication.class, args);
	}

}
