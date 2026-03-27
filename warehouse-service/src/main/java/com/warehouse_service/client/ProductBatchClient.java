package com.warehouse_service.client;

import com.common.api.ApiResponse;
import com.warehouse_service.dto.response.ProductSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "product-service", path = "/api/products")
public interface ProductBatchClient {

    @PostMapping("/batch")
    ApiResponse<List<ProductSummary>> getByIds(@RequestBody List<UUID> ids);
}

