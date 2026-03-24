package com.common.client.warehouse;

import com.common.api.ApiResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import feign.FeignException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(WarehouseStockClient.class)
public class WarehouseStockGateway {

    private final WarehouseStockClient warehouseStockClient;

    public WarehouseStockGateway(WarehouseStockClient warehouseStockClient) {
        this.warehouseStockClient = warehouseStockClient;
    }

    public void adjustOrThrow(StockAdjustCommand command) {
        try {
            ApiResponse<WarehouseStockData> res = warehouseStockClient.adjust(command);
            if (!res.isSuccess()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        res.getMessage() != null ? res.getMessage() : "Warehouse từ chối điều chỉnh tồn");
            }
        } catch (AppException e) {
            throw e;
        } catch (FeignException.BadRequest e) {
            throw new AppException(ErrorCode.BAD_REQUEST, extractMessage(e));
        } catch (FeignException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Gọi warehouse thất bại (HTTP " + e.status() + "): " + extractMessage(e));
        }
    }

    private String extractMessage(FeignException e) {
        String body = e.contentUTF8();
        return (body != null && !body.isBlank()) ? body : e.getMessage();
    }
}
