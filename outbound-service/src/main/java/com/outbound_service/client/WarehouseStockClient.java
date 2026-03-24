package com.outbound_service.client;

import com.common.api.ApiResponse;
import com.common.api.stock.StockAdjustCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "warehouse-service", path = "/api/stocks")
public interface WarehouseStockClient {

    @PostMapping("/adjust")
    ApiResponse<WarehouseStockData> adjust(@RequestBody StockAdjustCommand command);
}
