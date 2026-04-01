package com.outbound_service.client;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(contextId = "warehouseStockClient", name = "api-gateway", path = "/api/stocks")
public interface WarehouseStockClient {

    @PostMapping("/adjust")
    ApiResponse<WarehouseStockData> adjust(@RequestBody StockAdjustCommand command);

    @PostMapping("/adjust-reserved")
    ApiResponse<WarehouseStockData> adjustReserved(@RequestBody StockReserveCommand command);

    @GetMapping
    ApiResponse<PagedResponse<WarehouseStockData>> listStocks(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );
}
