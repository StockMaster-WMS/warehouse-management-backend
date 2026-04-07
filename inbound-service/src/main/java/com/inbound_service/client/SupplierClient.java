package com.inbound_service.client;

import com.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.OffsetDateTime;
import java.util.UUID;

@FeignClient(name = "product-service", contextId = "supplierClient", path = "/api/suppliers")
public interface SupplierClient {

    @GetMapping("/{id}")
    ApiResponse<SupplierData> getById(@PathVariable("id") UUID id);

    record SupplierData(
            UUID id,
            String code,
            String name,
            String taxCode,
            String contactName,
            String contactPhone,
            String contactEmail,
            String address,
            Short paymentTerms,
            Short leadTimeDays,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}