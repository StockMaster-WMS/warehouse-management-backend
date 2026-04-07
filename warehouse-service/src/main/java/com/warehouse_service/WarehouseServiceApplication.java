package com.warehouse_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.warehouse_service", "com.common"})
@EnableFeignClients(basePackages = {"com.warehouse_service"})
@EntityScan(basePackages = {"com.warehouse_service", "com.common"})
@EnableJpaRepositories(basePackages = {"com.warehouse_service", "com.common"})
public class WarehouseServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WarehouseServiceApplication.class, args);
	}

}
