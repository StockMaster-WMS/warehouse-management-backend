package com.outbound_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.outbound_service", "com.common"})
public class OutboundServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutboundServiceApplication.class, args);
	}

}
