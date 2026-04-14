package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.client.ProductBatchClient;
import com.warehouse_service.dto.request.CreateStockLevelRequest;
import com.warehouse_service.dto.request.UpdateStockLevelRequest;
import com.warehouse_service.dto.response.LocationSummary;
import com.warehouse_service.dto.response.NearExpiryStockResponse;
import com.warehouse_service.dto.response.ProductSummary;
import com.warehouse_service.dto.response.StockLevelExpandedResponse;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.warehouse_service.dto.response.StockSummaryResponse;
import com.warehouse_service.dto.response.WarehouseSummary;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.StockMovement;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.StockLevelMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.StockLevelSpecification;
import com.warehouse_service.repository.StockMovementRepository;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogService auditLogService;

    // Lấy danh sách tồn kho có phân trang và bộ lọc cơ bản.
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

    // Lấy danh sách tồn kho dạng mở rộng kèm thông tin liên quan.
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

    // Lấy chi tiết tồn kho theo id.
    public StockLevelResponse findById(UUID id) {
        StockLevel stock = getStockLevel(id);
        return fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock));
    }

    // Tạo mới bản ghi tồn kho.
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
        StockLevelResponse response = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
        auditLogService.record("STOCK", "CREATE", "Tạo tồn kho",
                "STOCK_LEVEL", saved.getId(), stockEntityName(saved), null, response,
                null, stockMetadata(saved, null, null, null, null));
        return response;
    }

    // Cập nhật bản ghi tồn kho theo id.
    @Transactional
    public StockLevelResponse update(UUID id, UpdateStockLevelRequest request) {
        validateQuantities(request.qtyOnHand(), request.qtyReserved());

        StockLevel stockLevel = getStockLevel(id);
        Warehouse warehouse = getWarehouse(request.warehouseId());
        Location location = getLocation(request.locationId());
        ensureLocationInWarehouse(location, request.warehouseId());

        String lotNumber = normalizeLot(request.lotNumber());
        ensureUniqueStock(location.getId(), request.productId(), lotNumber, id);
        StockLevelResponse before = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stockLevel));

        stockLevelMapper.updateEntity(request, stockLevel);
        stockLevel.setWarehouse(warehouse);
        stockLevel.setLocation(location);
        stockLevel.setLotNumber(lotNumber);

        StockLevel saved = stockLevelRepository.save(stockLevel);
        StockLevelResponse after = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
        auditLogService.record("STOCK", "UPDATE", "Cập nhật tồn kho",
                "STOCK_LEVEL", saved.getId(), stockEntityName(saved), before, after,
                null, stockMetadata(saved, null, null, null, null));
        return after;
    }

    // Xóa bản ghi tồn kho theo id.
    @Transactional
    public void delete(UUID id) {
        StockLevel stockLevel = getStockLevel(id);
        StockLevelResponse before = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stockLevel));
        stockLevelRepository.delete(stockLevel);
        auditLogService.record("STOCK", "DELETE", "Xóa tồn kho",
                "STOCK_LEVEL", id, stockEntityName(stockLevel), before, null,
                null, stockMetadata(stockLevel, null, null, null, null));
    }

    private static final int MAX_RETRY = 3;

    // Điều chỉnh tồn kho theo vị trí, sản phẩm, lô với cơ chế retry optimistic lock.
    @Transactional
    public StockLevelResponse adjust(StockAdjustCommand cmd) {
        if (cmd.qtyDelta() == 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "qtyDelta phải khác 0");
        }

        String lot = normalizeLot(cmd.lotNumber());
        Optional<StockLevelResponse> idempotentResponse = findIdempotentStockResponse(
                cmd.idempotencyKey(), cmd.locationId(), cmd.productId(), lot);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        Warehouse warehouse = getWarehouse(cmd.warehouseId());
        Location location = getLocation(cmd.locationId());
        ensureLocationInWarehouse(location, cmd.warehouseId());

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                return stockLevelRepository
                        .findByLocationIdAndProductIdAndLotNumber(cmd.locationId(), cmd.productId(), lot)
                        .map(existing -> applyDelta(existing, cmd.qtyDelta(), cmd))
                        .orElseGet(() -> createFromPositiveDelta(warehouse, location, cmd.productId(), lot, cmd));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
                }
            }
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
    }

    // Điều chỉnh lượng giữ chỗ reserved theo vị trí, sản phẩm, lô.
    @Transactional
    public StockLevelResponse adjustReserved(StockReserveCommand cmd) {
        if (cmd.reservedDelta() == 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "reservedDelta phải khác 0");
        }

        String lot = normalizeLot(cmd.lotNumber());
        Optional<StockLevelResponse> idempotentResponse = findIdempotentStockResponse(
                cmd.idempotencyKey(), cmd.locationId(), cmd.productId(), lot);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

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
                StockLevelResponse before = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock));
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

                recordMovement(saved, 0, cmd.reservedDelta(),
                        cmd.reservedDelta() > 0 ? "RESERVE" : "RELEASE",
                        cmd.idempotencyKey(), cmd.referenceType(), cmd.referenceId());

                StockLevelResponse after = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
                auditLogService.record("STOCK", "STOCK_RESERVE", "Điều chỉnh giữ chỗ tồn kho",
                        "STOCK_LEVEL", saved.getId(), stockEntityName(saved), before, after,
                        cmd.referenceType(), stockMetadata(saved, 0, cmd.reservedDelta(),
                                cmd.referenceType(), cmd.referenceId()));
                return after;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
                }
            }
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Tồn kho đang được cập nhật đồng thời, vui lòng thử lại");
    }

    // Tạo bản ghi tồn mới khi điều chỉnh dương trên khóa chưa tồn tại.
    private StockLevelResponse createFromPositiveDelta(Warehouse warehouse, Location location, UUID productId,
            String lot, StockAdjustCommand cmd) {
        if (cmd.qtyDelta() < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có tồn kho để trừ tại vị trí/sản phẩm/lô đã chọn");
        }
        validateQuantities(cmd.qtyDelta(), 0);

        StockLevel stockLevel = StockLevel.builder()
                .warehouse(warehouse)
                .location(location)
                .productId(productId)
                .lotNumber(lot)
                .qtyOnHand(cmd.qtyDelta())
                .qtyReserved(0)
                .build();

        StockLevel saved = stockLevelRepository.save(stockLevel);

        recordMovement(saved, cmd.qtyDelta(), 0, "INBOUND",
                cmd.idempotencyKey(), cmd.referenceType(), cmd.referenceId());

        StockLevelResponse after = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
        auditLogService.record("STOCK", "STOCK_ADJUST", "Điều chỉnh tồn kho",
                "STOCK_LEVEL", saved.getId(), stockEntityName(saved), null, after,
                cmd.referenceType(), stockMetadata(saved, cmd.qtyDelta(), 0, cmd.referenceType(), cmd.referenceId()));
        return after;
    }

    // Áp dụng biến động tồn lên bản ghi hiện có.
    private StockLevelResponse applyDelta(StockLevel stockLevel, int qtyDelta, StockAdjustCommand cmd) {
        StockLevelResponse before = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stockLevel));
        int newOnHand = stockLevel.getQtyOnHand() + qtyDelta;
        if (newOnHand < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng trừ vượt quá tồn khả dụng");
        }
        int reserved = stockLevel.getQtyReserved() == null ? 0 : stockLevel.getQtyReserved();
        validateQuantities(newOnHand, reserved);

        stockLevel.setQtyOnHand(newOnHand);
        StockLevel saved = stockLevelRepository.save(stockLevel);

        recordMovement(saved, qtyDelta, 0,
                qtyDelta > 0 ? "INBOUND" : "OUTBOUND",
                cmd.idempotencyKey(), cmd.referenceType(), cmd.referenceId());

        StockLevelResponse after = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(saved));
        auditLogService.record("STOCK", "STOCK_ADJUST", "Điều chỉnh tồn kho",
                "STOCK_LEVEL", saved.getId(), stockEntityName(saved), before, after,
                cmd.referenceType(), stockMetadata(saved, qtyDelta, 0, cmd.referenceType(), cmd.referenceId()));
        return after;
    }

    // Tìm thực thể tồn kho theo id.
    private StockLevel getStockLevel(UUID id) {
        return stockLevelRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tồn kho"));
    }

    // Tìm thực thể kho theo id.
    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

    // Tìm thực thể vị trí theo id.
    private Location getLocation(UUID id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí"));
    }

    // Đảm bảo vị trí thuộc đúng kho được yêu cầu.
    private void ensureLocationInWarehouse(Location location, UUID warehouseId) {
        if (!location.getWarehouse().getId().equals(warehouseId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí không thuộc kho đã chọn");
        }
    }

    // Đảm bảo không trùng tồn kho theo tổ hợp vị trí-sản phẩm-lô.
    private void ensureUniqueStock(UUID locationId, UUID productId, String lotNumber, UUID currentStockId) {
        stockLevelRepository.findByLocationIdAndProductIdAndLotNumber(locationId, productId, lotNumber)
                .filter(existing -> currentStockId == null || !existing.getId().equals(currentStockId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Tồn kho theo vị trí/sản phẩm/lô đã tồn tại");
                });
    }

    // Kiểm tra hợp lệ của số lượng tồn tay và giữ chỗ.
    private void validateQuantities(Integer qtyOnHand, Integer qtyReserved) {
        int reserved = qtyReserved == null ? 0 : qtyReserved;
        if (qtyOnHand < 0 || reserved < 0 || reserved > qtyOnHand) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng tồn hoặc giữ chỗ không hợp lệ");
        }
    }

    // Chuẩn hóa lot number về chuỗi không null.
    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    // Bổ sung qtyAvailable nếu mapper chưa trả giá trị này.
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

    // Trích danh sách warehouseId từ tập tồn kho.
    private Set<UUID> extractWarehouseIds(Collection<StockLevel> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevel s : stocks) {
            if (s.getWarehouse() != null && s.getWarehouse().getId() != null) {
                ids.add(s.getWarehouse().getId());
            }
        }
        return ids;
    }

    // Trích danh sách locationId từ tập tồn kho.
    private Set<UUID> extractLocationIds(Collection<StockLevel> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevel s : stocks) {
            if (s.getLocation() != null && s.getLocation().getId() != null) {
                ids.add(s.getLocation().getId());
            }
        }
        return ids;
    }

    // Trích danh sách productId từ tập tồn kho.
    private Set<UUID> extractProductIds(Collection<StockLevel> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevel s : stocks) {
            if (s.getProductId() != null) {
                ids.add(s.getProductId());
            }
        }
        return ids;
    }

    // Tải thông tin tóm tắt kho theo danh sách id.
    private Map<UUID, WarehouseSummary> loadWarehousesSummary(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Warehouse> warehouses = warehouseRepository.findAllById(ids);
        Map<UUID, WarehouseSummary> map = new HashMap<>(warehouses.size());
        for (Warehouse w : warehouses) {
            map.put(w.getId(), new WarehouseSummary(w.getId(), w.getCode(), w.getName()));
        }
        return map;
    }

    // Tải thông tin tóm tắt vị trí theo danh sách id.
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

    // Tải thông tin tóm tắt sản phẩm theo danh sách id.
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

    // Ghi log biến động tồn kho.
    private void recordMovement(StockLevel stock, int qtyChange, int reservedChange, String movementType,
            String idempotencyKey, String referenceType, UUID referenceId) {
        StockMovement movement = StockMovement.builder()
                .warehouse(stock.getWarehouse())
                .location(stock.getLocation())
                .productId(stock.getProductId())
                .lotNumber(stock.getLotNumber())
                .movementType(movementType)
                .qtyChange(qtyChange)
                .qtyAfter(stock.getQtyOnHand())
                .reservedChange(reservedChange)
                .reservedAfter(stock.getQtyReserved() == null ? 0 : stock.getQtyReserved())
                .idempotencyKey(normalizeIdempotencyKey(idempotencyKey))
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
        stockMovementRepository.save(movement);
    }

    private Optional<StockLevelResponse> findIdempotentStockResponse(String idempotencyKey,
            UUID locationId, UUID productId, String lot) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        Optional<StockMovement> movement = stockMovementRepository.findByIdempotencyKey(
                normalizeIdempotencyKey(idempotencyKey));
        if (movement.isEmpty()) {
            return Optional.empty();
        }

        StockLevel stock = stockLevelRepository
                .findByLocationIdAndProductIdAndLotNumber(locationId, productId, lot)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy tồn kho cho lệnh đã xử lý trước đó"));
        return Optional.of(fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock)));
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private String stockEntityName(StockLevel stock) {
        String locationCode = stock.getLocation() == null ? null : stock.getLocation().getCode();
        String lot = normalizeLot(stock.getLotNumber());
        return "product=" + stock.getProductId()
                + (locationCode == null ? "" : ", location=" + locationCode)
                + (lot.isBlank() ? "" : ", lot=" + lot);
    }

    private Map<String, Object> stockMetadata(StockLevel stock, Integer qtyDelta, Integer reservedDelta,
            String referenceType, UUID referenceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("warehouseId", stock.getWarehouse() == null ? null : stock.getWarehouse().getId());
        metadata.put("locationId", stock.getLocation() == null ? null : stock.getLocation().getId());
        metadata.put("productId", stock.getProductId());
        metadata.put("lotNumber", normalizeLot(stock.getLotNumber()));
        if (qtyDelta != null) {
            metadata.put("qtyDelta", qtyDelta);
        }
        if (reservedDelta != null) {
            metadata.put("reservedDelta", reservedDelta);
        }
        if (StringUtils.hasText(referenceType)) {
            metadata.put("referenceType", referenceType);
        }
        if (referenceId != null) {
            metadata.put("referenceId", referenceId);
        }
        return metadata;
    }

    // Lấy số liệu tổng quan tồn kho phục vụ dashboard.
    public StockSummaryResponse getSummary(int nearExpiryDays) {
        long totalSkus = stockLevelRepository.countDistinctProducts();
        long totalOnHand = stockLevelRepository.sumTotalQtyOnHand();
        long totalReserved = stockLevelRepository.sumTotalQtyReserved();
        long nearExpiry = stockLevelRepository.countNearExpiry(LocalDate.now().plusDays(nearExpiryDays));

        // Đếm low-stock: lấy tất cả stock, so minQty từ product-service
        long lowStockCount = countLowStock();

        return new StockSummaryResponse(
                totalSkus,
                totalOnHand,
                totalReserved,
                totalOnHand - totalReserved,
                lowStockCount,
                nearExpiry);
    }

    // Lấy danh sách sản phẩm tồn kho thấp (qtyAvailable < minQty).
    public List<StockLevelExpandedResponse> findLowStock() {
        List<StockLevelRepository.StockQuantityView> stockViews = stockLevelRepository.findQuantityViews();
        if (stockViews.isEmpty()) return List.of();

        Map<UUID, ProductSummary> productMap = loadProductsSummary(extractProductIdsFromQuantityViews(stockViews));
        if (productMap.isEmpty()) return List.of();

        List<UUID> lowStockIds = new ArrayList<>();
        for (StockLevelRepository.StockQuantityView stock : stockViews) {
            if (isLowStock(stock, productMap)) {
                lowStockIds.add(stock.getId());
            }
        }
        if (lowStockIds.isEmpty()) return List.of();

        List<StockLevel> lowStocks = stockLevelRepository.findByIdInWithWarehouseAndLocation(lowStockIds);
        Map<UUID, StockLevel> stockById = new HashMap<>(lowStocks.size());
        for (StockLevel stock : lowStocks) {
            stockById.put(stock.getId(), stock);
        }

        Map<UUID, WarehouseSummary> warehouseMap = loadWarehousesSummary(extractWarehouseIds(lowStocks));
        Map<UUID, LocationSummary> locationMap = loadLocationsSummary(extractLocationIds(lowStocks));

        List<StockLevelExpandedResponse> result = new ArrayList<>();
        for (UUID stockId : lowStockIds) {
            StockLevel stock = stockById.get(stockId);
            if (stock == null) continue;
            ProductSummary product = productMap.get(stock.getProductId());
            if (product == null) continue;

            StockLevelResponse base = fillQtyAvailableWhenMissing(stockLevelMapper.toResponse(stock));
            result.add(new StockLevelExpandedResponse(
                    base.id(), base.warehouseId(), base.locationId(), base.productId(),
                    base.lotNumber(), base.expiryDate(),
                    base.qtyOnHand(), base.qtyReserved(), base.qtyAvailable(), base.updatedAt(),
                    warehouseMap.get(base.warehouseId()),
                    locationMap.get(base.locationId()),
                    product));
        }
        return result;
    }

    // Lấy danh sách hàng sắp hết hạn dạng JSON.
    public List<NearExpiryStockResponse> findNearExpiry(int days,
            UUID warehouseId, UUID locationId, UUID productId) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        List<StockLevel> stocks = stockLevelRepository.findNearExpiry(threshold, warehouseId, locationId, productId);

        return stocks.stream()
                .map(s -> {
                    long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), s.getExpiryDate());
                    int onHand = s.getQtyOnHand() == null ? 0 : s.getQtyOnHand();
                    int reserved = s.getQtyReserved() == null ? 0 : s.getQtyReserved();
                    return new NearExpiryStockResponse(
                            s.getId(),
                            s.getWarehouse().getId(),
                            s.getWarehouse().getCode(),
                            s.getLocation().getId(),
                            s.getLocation().getCode(),
                            s.getProductId(),
                            s.getLotNumber(),
                            s.getExpiryDate(),
                            daysLeft,
                            onHand,
                            reserved,
                            onHand - reserved);
                })
                .toList();
    }

    // Đếm số stock record có qtyAvailable < minQty.
    private long countLowStock() {
        List<StockLevelRepository.StockQuantityView> stockViews = stockLevelRepository.findQuantityViews();
        if (stockViews.isEmpty()) return 0;

        Map<UUID, ProductSummary> productMap = loadProductsSummary(extractProductIdsFromQuantityViews(stockViews));

        long count = 0;
        for (StockLevelRepository.StockQuantityView stock : stockViews) {
            if (isLowStock(stock, productMap)) count++;
        }
        return count;
    }

    private Set<UUID> extractProductIdsFromQuantityViews(Collection<StockLevelRepository.StockQuantityView> stocks) {
        Set<UUID> ids = new HashSet<>();
        for (StockLevelRepository.StockQuantityView stock : stocks) {
            if (stock.getProductId() != null) {
                ids.add(stock.getProductId());
            }
        }
        return ids;
    }

    private boolean isLowStock(StockLevelRepository.StockQuantityView stock, Map<UUID, ProductSummary> productMap) {
        ProductSummary product = productMap.get(stock.getProductId());
        if (product == null || product.minQty() == null) {
            return false;
        }
        int onHand = stock.getQtyOnHand() == null ? 0 : stock.getQtyOnHand();
        int reserved = stock.getQtyReserved() == null ? 0 : stock.getQtyReserved();
        return onHand - reserved < product.minQty();
    }
}
