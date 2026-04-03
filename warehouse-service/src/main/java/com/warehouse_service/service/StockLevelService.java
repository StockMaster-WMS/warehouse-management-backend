package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.client.ProductBatchClient;
import com.warehouse_service.dto.request.CreateStockLevelRequest;
import com.warehouse_service.dto.request.UpdateStockLevelRequest;
import com.warehouse_service.dto.response.LocationSummary;
import com.warehouse_service.dto.response.ProductSummary;
import com.warehouse_service.dto.response.StockLevelExpandedResponse;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.warehouse_service.dto.response.WarehouseSummary;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.StockLevelMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.StockLevelSpecification;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockLevelService {

    private final StockLevelRepository stockLevelRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final StockLevelMapper stockLevelMapper;
    private final ProductBatchClient productBatchClient;

    public PagedResponse<StockLevelResponse> findAll(Pageable pageable, UUID warehouseId, UUID locationId, UUID productId) {
        Specification<StockLevel> spec = StockLevelSpecification.hasWarehouseId(warehouseId)
                .and(StockLevelSpecification.hasLocationId(locationId))
                .and(StockLevelSpecification.hasProductId(productId));
        Page<StockLevel> page = stockLevelRepository.findAll(spec, pageable);
        List<StockLevelResponse> content = new ArrayList<>(page.getContent().size());
        for (StockLevel stock : page.getContent()) {
            content.add(fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock)));
        }
        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public PagedResponse<StockLevelExpandedResponse> findAllExpanded(Pageable pageable,
            UUID warehouseId, UUID locationId, UUID productId,
            boolean expandWarehouse, boolean expandLocation, boolean expandProduct) {
        Specification<StockLevel> spec = StockLevelSpecification.hasWarehouseId(warehouseId)
                .and(StockLevelSpecification.hasLocationId(locationId))
                .and(StockLevelSpecification.hasProductId(productId));

        Page<StockLevel> page = stockLevelRepository.findAll(spec, pageable);
        List<StockLevel> stocks = page.getContent();

        Map<UUID, WarehouseSummary> warehouseMap = expandWarehouse
                ? loadWarehousesSummary(extractWarehouseIds(stocks))
                : Map.of();
        Map<UUID, LocationSummary> locationMap = expandLocation
                ? loadLocationsSummary(extractLocationIds(stocks))
                : Map.of();
        Map<UUID, ProductSummary> productMap = expandProduct
                ? loadProductsSummary(extractProductIds(stocks))
                : Map.of();

        List<StockLevelExpandedResponse> content = new ArrayList<>(stocks.size());
        for (StockLevel stock : stocks) {
            StockLevelResponse base = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock));
            UUID wid = base.warehouseId();
            UUID lid = base.locationId();
            UUID pid = base.productId();
            content.add(new StockLevelExpandedResponse(
                    base.id(),
                    wid,
                    lid,
                    pid,
                    base.lotNumber(),
                    base.expiryDate(),
                    base.qtyOnHand(),
                    base.qtyReserved(),
                    base.qtyAvailable(),
                    base.updatedAt(),
                    expandWarehouse ? warehouseMap.get(wid) : null,
                    expandLocation ? locationMap.get(lid) : null,
                    expandProduct ? productMap.get(pid) : null
            ));
        }

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
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

    private static final int MAX_RETRY = 3;

    /**
     * Tăng/giảm tồn theo vị trí + sản phẩm + lô. qtyDelta > 0: nhập; < 0: xuất/trừ.
     * Sử dụng optimistic locking (@Version) để đảm bảo tính nhất quán khi có concurrent requests.
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

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                return stockLevelRepository
                        .findByLocationIdAndProductIdAndLotNumber(cmd.locationId(), cmd.productId(), lot)
                        .map(existing -> applyDelta(existing, cmd.qtyDelta()))
                        .orElseGet(() -> createFromPositiveDelta(warehouse, location, cmd.productId(), lot, cmd.qtyDelta()));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
                }
            }
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
    }

    /**
     * Điều chỉnh lượng giữ chỗ. delta &gt; 0: cần đủ tồn khả dụng (on_hand − reserved); delta &lt; 0: nhả chỗ.
     */
    @Transactional
    public StockLevelResponse adjustReserved(StockReserveCommand cmd) {
        if (cmd.reservedDelta() == 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "reservedDelta phải khác 0");
        }

        String lot = normalizeLot(cmd.lotNumber());
        getWarehouse(cmd.warehouseId());
        Location location = getLocation(cmd.locationId());
        ensureLocationInWarehouse(location, cmd.warehouseId());

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                Optional<StockLevel> opt = stockLevelRepository
                        .findByLocationIdAndProductIdAndLotNumber(cmd.locationId(), cmd.productId(), lot);
                if (opt.isEmpty()) {
                    if (cmd.reservedDelta() > 0) {
                        throw new AppException(ErrorCode.BAD_REQUEST,
                                "Không có bản ghi tồn kho tại vị trí/sản phẩm/lô để giữ chỗ");
                    }
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "Không có tồn kho để nhả chỗ tại vị trí/sản phẩm/lô đã chọn");
                }
                StockLevel stock = opt.get();
                int onHand = stock.getQtyOnHand();
                int reserved = stock.getQtyReserved() == null ? 0 : stock.getQtyReserved();
                int newReserved = reserved + cmd.reservedDelta();
                if (newReserved < 0) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Không đủ lượng đang giữ chỗ để nhả");
                }
                if (newReserved > onHand) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Giữ chỗ không được vượt quá tồn tay");
                }
                if (cmd.reservedDelta() > 0) {
                    int available = onHand - reserved;
                    if (cmd.reservedDelta() > available) {
                        throw new AppException(ErrorCode.BAD_REQUEST,
                                "Không đủ tồn khả dụng để giữ chỗ (khả dụng: " + available + ")");
                    }
                }
                validateQuantities(onHand, newReserved);
                stock.setQtyReserved(newReserved);
                StockLevel saved = stockLevelRepository.save(stock);
                return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
                }
            }
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
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
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí không thuộc kho đã chọn");
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

    private Set<UUID> extractWarehouseIds(Collection<StockLevel> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevel s : stocks) {
            if (s.getWarehouse() != null && s.getWarehouse().getId() != null) {
                ids.add(s.getWarehouse().getId());
            }
        }
        return ids;
    }

    private Set<UUID> extractLocationIds(Collection<StockLevel> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevel s : stocks) {
            if (s.getLocation() != null && s.getLocation().getId() != null) {
                ids.add(s.getLocation().getId());
            }
        }
        return ids;
    }

    private Set<UUID> extractProductIds(Collection<StockLevel> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevel s : stocks) {
            if (s.getProductId() != null) {
                ids.add(s.getProductId());
            }
        }
        return ids;
    }

    private Map<UUID, WarehouseSummary> loadWarehousesSummary(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Warehouse> warehouses = warehouseRepository.findAllById(ids);
        Map<UUID, WarehouseSummary> map = new HashMap<>(warehouses.size());
        for (Warehouse w : warehouses) {
            map.put(w.getId(), new WarehouseSummary(w.getId(), w.getCode(), w.getName()));
        }
        return map;
    }

    private Map<UUID, LocationSummary> loadLocationsSummary(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Location> locations = locationRepository.findAllById(ids);
        Map<UUID, LocationSummary> map = new HashMap<>(locations.size());
        for (Location l : locations) {
            // Location entity hiện không có field "name" -> dùng code làm name (UI-friendly)
            map.put(l.getId(), new LocationSummary(l.getId(), l.getCode(), l.getCode()));
        }
        return map;
    }

    private Map<UUID, ProductSummary> loadProductsSummary(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            List<UUID> list = new ArrayList<>(ids);
            var resp = productBatchClient.getByIds(list);
            List<ProductSummary> products = resp == null ? null : resp.getData();
            if (products == null || products.isEmpty()) return Map.of();
            Map<UUID, ProductSummary> map = new HashMap<>(products.size());
            for (ProductSummary p : products) {
                if (p != null && p.id() != null) {
                    map.put(p.id(), p);
                }
            }
            return map;
        } catch (Exception e) {
            // Degrade gracefully: vẫn trả stock, chỉ không expand product
            log.warn("Failed to load product summaries for {} product IDs: {}", ids.size(), e.getMessage());
            return Map.of();
        }
    }
}