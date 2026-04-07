package com.product_service.config;

import com.product_service.client.InboundClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = {InboundClient.class})
public class FeignConfig {
}
