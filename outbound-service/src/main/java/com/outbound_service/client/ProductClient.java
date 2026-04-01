package com.outbound_service.client;

import com.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "product-service", path = "/api/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ApiResponse<ProductDetailData> getProductById(@PathVariable UUID id);

    record ProductDetailData(
            UUID id,
            String sku,
            String barcodeEan13,
            String name,
            UUID categoryId,
            String categoryName,
            UUID primarySupplierId,
            String baseUnit
    ) {}
}
