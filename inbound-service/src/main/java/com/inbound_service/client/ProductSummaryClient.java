package com.inbound_service.client;

import com.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "product-service", path = "/api/products")
public interface ProductSummaryClient {

    @PostMapping("/batch")
    ApiResponse<List<ProductSummaryData>> getByIds(@RequestBody List<UUID> ids);
}