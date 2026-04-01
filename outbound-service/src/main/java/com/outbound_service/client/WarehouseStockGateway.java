package com.outbound_service.client;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseStockGateway {

    private final WarehouseStockClient warehouseStockClient;

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

    public void adjustReservedOrThrow(StockReserveCommand command) {
        try {
            ApiResponse<WarehouseStockData> res = warehouseStockClient.adjustReserved(command);
            if (!res.isSuccess()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        res.getMessage() != null ? res.getMessage() : "Warehouse từ chối điều chỉnh giữ chỗ");
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

    /**
     * Lấy toàn bộ bản ghi tồn (phân trang nội bộ) để phân bổ picking theo vị trí/lô.
     */
    public List<WarehouseStockData> listAllStocksForProduct(UUID warehouseId, UUID productId) {
        List<WarehouseStockData> all = new ArrayList<>();
        int page = 0;
        final int size = 100;
        try {
            while (true) {
                ApiResponse<PagedResponse<WarehouseStockData>> res = warehouseStockClient.listStocks(
                        warehouseId, null, productId, page, size);
                if (!res.isSuccess() || res.getData() == null) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            res.getMessage() != null ? res.getMessage() : "Không đọc được tồn kho từ warehouse");
                }
                PagedResponse<WarehouseStockData> pr = res.getData();
                List<WarehouseStockData> content = pr.content() != null ? pr.content() : List.of();
                all.addAll(content);
                if (content.isEmpty() || pr.totalPages() == 0 || page >= pr.totalPages() - 1) {
                    break;
                }
                page++;
            }
            return all;
        } catch (AppException e) {
            throw e;
        } catch (FeignException.BadRequest e) {
            throw new AppException(ErrorCode.BAD_REQUEST, extractMessage(e));
        } catch (FeignException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Gọi warehouse thất bại (HTTP " + e.status() + "): " + extractMessage(e));
        }
    }

    public void requireOnHandAtLeast(UUID warehouseId, UUID locationId, UUID productId, String lotNumber, int minOnHand) {
        if (minOnHand <= 0) {
            return;
        }
        WarehouseStockData row = getSingleStockRow(warehouseId, locationId, productId, lotNumber);
        int onHand = row.qtyOnHand() == null ? 0 : row.qtyOnHand();
        if (onHand < minOnHand) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không đủ tồn tay tại vị trí để pick (tồn tay: " + onHand + ", cần: " + minOnHand + ")");
        }
    }

    public WarehouseStockData getSingleStockRow(UUID warehouseId, UUID locationId, UUID productId, String lotNumber) {
        String lot = normalizeLot(lotNumber);
        try {
            ApiResponse<PagedResponse<WarehouseStockData>> res = warehouseStockClient.listStocks(
                    warehouseId, locationId, productId, 0, 20);
            if (!res.isSuccess() || res.getData() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        res.getMessage() != null ? res.getMessage() : "Không đọc được tồn kho từ warehouse");
            }
            List<WarehouseStockData> rows = res.getData().content();
            if (rows == null || rows.isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Không có bản ghi tồn kho tại vị trí/sản phẩm để pick");
            }
            return rows.stream()
                    .filter(r -> warehouseId.equals(r.warehouseId())
                            && locationId.equals(r.locationId())
                            && productId.equals(r.productId())
                            && lot.equals(normalizeLot(r.lotNumber())))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                            "Không có bản ghi tồn kho tại vị trí/sản phẩm/lô để pick"));
        } catch (AppException e) {
            throw e;
        } catch (FeignException.BadRequest e) {
            throw new AppException(ErrorCode.BAD_REQUEST, extractMessage(e));
        } catch (FeignException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Gọi warehouse thất bại (HTTP " + e.status() + "): " + extractMessage(e));
        }
    }

    /**
     * Lấy bản ghi single stock; nếu không tìm thấy, trả về null thay vì throw exception.
     * Dùng khi chỉ cần check qtyAvailable mà không cần fail hard.
     */
    public WarehouseStockData getSingleStockRowIfExists(UUID warehouseId, UUID locationId, UUID productId, String lotNumber) {
        String lot = normalizeLot(lotNumber);
        try {
            ApiResponse<PagedResponse<WarehouseStockData>> res = warehouseStockClient.listStocks(
                    warehouseId, locationId, productId, 0, 20);
            if (!res.isSuccess() || res.getData() == null) {
                return null;
            }
            List<WarehouseStockData> rows = res.getData().content();
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            return rows.stream()
                    .filter(r -> warehouseId.equals(r.warehouseId())
                            && locationId.equals(r.locationId())
                            && productId.equals(r.productId())
                            && lot.equals(normalizeLot(r.lotNumber())))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            // Silently return null on any error
            return null;
        }
    }

    private static String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private static String extractMessage(FeignException e) {
        String body = e.contentUTF8();
        return (body != null && !body.isBlank()) ? body : e.getMessage();
    }
}
