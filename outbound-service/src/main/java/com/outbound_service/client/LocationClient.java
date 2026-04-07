package com.outbound_service.client;

import com.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(contextId = "locationClient", name = "warehouse-service", path = "/api/locations")
public interface LocationClient {

    @GetMapping("/{id}")
    ApiResponse<LocationDetailData> getLocationById(@PathVariable UUID id);

    record LocationDetailData(
            UUID id,
            String code,
            String name,
            String zone,
            String aisle,
            String shelf,
            String position
    ) {}
}
