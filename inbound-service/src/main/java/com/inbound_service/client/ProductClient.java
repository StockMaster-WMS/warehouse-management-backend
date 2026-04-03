package com.inbound_service.client;

import com.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "product-service", path = "/api/products")
public interface ProductClient {

    @PostMapping
    ApiResponse<ProductData> createProduct(@RequestBody CreateProductCommand command);

    @GetMapping("/{id}")
    ApiResponse<ProductData> getById(@PathVariable UUID id);

    @PostMapping("/batch")
    ApiResponse<List<ProductSummaryData>> getByIds(@RequestBody List<UUID> ids);
}
