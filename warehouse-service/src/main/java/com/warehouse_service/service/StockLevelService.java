package com.warehouse_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateStockLevelRequest;
import com.warehouse_service.dto.request.UpdateStockLevelRequest;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.StockLevelMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockLevelService {

    private final StockLevelRepository stockLevelRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final StockLevelMapper stockLevelMapper;

    public List<StockLevelResponse> findAll(UUID warehouseId, UUID locationId, UUID productId) {
        List<StockLevel> stocks;

        if (locationId != null) {
            stocks = stockLevelRepository.findByLocationId(locationId);
        } else if (warehouseId != null && productId != null) {
            stocks = stockLevelRepository.findByWarehouseIdAndProductId(warehouseId, productId);
        } else if (warehouseId != null) {
            stocks = stockLevelRepository.findByWarehouseId(warehouseId);
        } else if (productId != null) {
            stocks = stockLevelRepository.findByProductId(productId);
        } else {
            stocks = stockLevelRepository.findAll();
        }

        return stocks.stream()
                .filter(stock -> warehouseId == null || stock.getWarehouse().getId().equals(warehouseId))
                .filter(stock -> locationId == null || stock.getLocation().getId().equals(locationId))
                .filter(stock -> productId == null || stock.getProductId().equals(productId))
                .map(stockLevelMapper::toResponse)
                .map(this::fillQtyAvailableWhenMissing)
                .toList();
    }

    public StockLevelResponse findById(UUID id) {
        StockLevel stock = getStockLevel(id);
        return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock));
    }

    @Transactional
    public StockLevelResponse create(CreateStockLevelRequest request) {
        validateQuantities(request.qtyOnHand(), request.qtyReserved());

        Warehouse warehouse = getWarehouse(request.warehouseId());
        Location location = getLocation(request.locationId());
        ensureLocationInWarehouse(location, request.warehouseId());

        String lotNumber = normalizeLot(request.lotNumber());
        ensureUniqueStock(location.getId(), request.productId(), lotNumber, null);

        StockLevel stockLevel = stockLevelMapper.toEntity(request);
        stockLevel.setWarehouse(warehouse);
        stockLevel.setLocation(location);
        stockLevel.setLotNumber(lotNumber);

        StockLevel saved = stockLevelRepository.save(stockLevel);
        return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
    }

    @Transactional
    public StockLevelResponse update(UUID id, UpdateStockLevelRequest request) {
        validateQuantities(request.qtyOnHand(), request.qtyReserved());

        StockLevel stockLevel = getStockLevel(id);
        Warehouse warehouse = getWarehouse(request.warehouseId());
        Location location = getLocation(request.locationId());
        ensureLocationInWarehouse(location, request.warehouseId());

        String lotNumber = normalizeLot(request.lotNumber());
        ensureUniqueStock(location.getId(), request.productId(), lotNumber, id);

        stockLevelMapper.updateEntity(request, stockLevel);
        stockLevel.setWarehouse(warehouse);
        stockLevel.setLocation(location);
        stockLevel.setLotNumber(lotNumber);

        StockLevel saved = stockLevelRepository.save(stockLevel);
        return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
    }

    @Transactional
    public void delete(UUID id) {
        StockLevel stockLevel = getStockLevel(id);
        stockLevelRepository.delete(stockLevel);
    }

    /**
     * Tăng/giảm tồn theo vị trí + sản phẩm + lô. qtyDelta &gt; 0: nhập; &lt; 0: xuất/trừ.
     * Nếu chưa có dòng tồn và qtyDelta &gt; 0 thì tạo mới.
     */
    @Transactional
    public StockLevelResponse adjust(StockAdjustCommand cmd) {
        if (cmd.qtyDelta() == 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "qtyDelta phải khác 0");
        }

        String lot = normalizeLot(cmd.lotNumber());
        Warehouse warehouse = getWarehouse(cmd.warehouseId());
        Location location = getLocation(cmd.locationId());
        ensureLocationInWarehouse(location, cmd.warehouseId());

        return stockLevelRepository
                .findByLocationIdAndProductIdAndLotNumber(cmd.locationId(), cmd.productId(), lot)
                .map(existing -> applyDelta(existing, cmd.qtyDelta()))
                .orElseGet(() -> createFromPositiveDelta(warehouse, location, cmd.productId(), lot, cmd.qtyDelta()));
    }

    private StockLevelResponse createFromPositiveDelta(Warehouse warehouse, Location location, UUID productId,
            String lot, int qtyDelta) {
        if (qtyDelta < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có tồn kho để trừ tại vị trí/sản phẩm/lô đã chọn");
        }
        validateQuantities(qtyDelta, 0);

        StockLevel stockLevel = StockLevel.builder()
                .warehouse(warehouse)
                .location(location)
                .productId(productId)
                .lotNumber(lot)
                .qtyOnHand(qtyDelta)
                .qtyReserved(0)
                .build();

        StockLevel saved = stockLevelRepository.save(stockLevel);
        return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
    }

    private StockLevelResponse applyDelta(StockLevel stockLevel, int qtyDelta) {
        int newOnHand = stockLevel.getQtyOnHand() + qtyDelta;
        if (newOnHand < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng trừ vượt quá tồn khả dụng");
        }
        int reserved = stockLevel.getQtyReserved() == null ? 0 : stockLevel.getQtyReserved();
        validateQuantities(newOnHand, reserved);

        stockLevel.setQtyOnHand(newOnHand);
        StockLevel saved = stockLevelRepository.save(stockLevel);
        return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
    }

    private StockLevel getStockLevel(UUID id) {
        return stockLevelRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tồn kho"));
    }

    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

    private Location getLocation(UUID id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí"));
    }

    private void ensureLocationInWarehouse(Location location, UUID warehouseId) {
        if (!location.getWarehouse().getId().equals(warehouseId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vi tri không thuộc kho đã chọn");
        }
    }

    private void ensureUniqueStock(UUID locationId, UUID productId, String lotNumber, UUID currentStockId) {
        stockLevelRepository.findByLocationIdAndProductIdAndLotNumber(locationId, productId, lotNumber)
                .filter(existing -> currentStockId == null || !existing.getId().equals(currentStockId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Tồn kho theo vị trí/sản phẩm/lô đã tồn tại");
                });
    }

    private void validateQuantities(Integer qtyOnHand, Integer qtyReserved) {
        int reserved = qtyReserved == null ? 0 : qtyReserved;
        if (qtyOnHand < 0 || reserved < 0 || reserved > qtyOnHand) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng tồn hoặc giữ chỗ không hợp lệ");
        }
    }

    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private StockLevelResponse fillQtyAvailableWhenMissing(StockLevelResponse response) {
        if (response.qtyAvailable() != null) {
            return response;
        }

        int qtyReserved = response.qtyReserved() == null ? 0 : response.qtyReserved();
        return new StockLevelResponse(
                response.id(),
                response.warehouseId(),
                response.locationId(),
                response.productId(),
                response.lotNumber(),
                response.expiryDate(),
                response.qtyOnHand(),
                response.qtyReserved(),
                response.qtyOnHand() - qtyReserved,
                response.updatedAt()
        );
    }
}