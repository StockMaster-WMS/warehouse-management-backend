package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.dto.response.WarehouseSummaryResponse;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.WarehouseMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import com.warehouse_service.repository.WarehouseSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final StockLevelRepository stockLevelRepository;
    private final WarehouseMapper warehouseMapper;

    public PagedResponse<WarehouseResponse> findAll(Pageable pageable, String keyword, Boolean isActive,
            String timezone) {
        Specification<Warehouse> spec = WarehouseSpecification
                .hasKeyword(keyword)
                .and(WarehouseSpecification.hasActive(isActive))
                .and(WarehouseSpecification.hasTimezone(timezone));

        Page<Warehouse> page = warehouseRepository.findAll(spec, pageable);
        Map<UUID, WarehouseMetrics> metricsByWarehouseId = loadMetrics(page.getContent().stream()
            .map(Warehouse::getId)
            .toList());

        List<WarehouseResponse> content = page.getContent().stream()
            .map(warehouse -> toEnrichedResponse(warehouse, metricsByWarehouseId.get(warehouse.getId())))
            .toList();

        return new PagedResponse<>(
            content,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
        }

        public WarehouseSummaryResponse getSummary() {
        long totalWarehouses = warehouseRepository.count();
        long activeWarehouses = warehouseRepository.countByIsActiveTrue();
        long inactiveWarehouses = warehouseRepository.countByIsActiveFalse();
        long warehousesWithStock = stockLevelRepository.countWarehousesWithStock();

        List<UUID> warehouseIds = warehouseRepository.findAll().stream()
            .map(Warehouse::getId)
            .toList();
        Map<UUID, WarehouseMetrics> metricsByWarehouseId = loadMetrics(warehouseIds);

        long highFillRateWarehouses = metricsByWarehouseId.values().stream()
            .map(this::calculateFillRatePercent)
            .filter(fillRate -> fillRate >= 80.0)
            .count();

        return new WarehouseSummaryResponse(
            totalWarehouses,
            activeWarehouses,
            inactiveWarehouses,
            warehousesWithStock,
            highFillRateWarehouses);
    }

    public WarehouseResponse findById(UUID id) {
        Warehouse warehouse = getWarehouse(id);
        Map<UUID, WarehouseMetrics> metricsByWarehouseId = loadMetrics(List.of(id));
        return toEnrichedResponse(warehouse, metricsByWarehouseId.get(id));
    }

    public WarehouseResponse findByCode(String code) {
        Warehouse warehouse = warehouseRepository.findByCode(code)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
        Map<UUID, WarehouseMetrics> metricsByWarehouseId = loadMetrics(List.of(warehouse.getId()));
        return toEnrichedResponse(warehouse, metricsByWarehouseId.get(warehouse.getId()));
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã kho đã tồn tại");
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);

        Warehouse saved = warehouseRepository.save(warehouse);
        Map<UUID, WarehouseMetrics> metricsByWarehouseId = loadMetrics(List.of(saved.getId()));
        return toEnrichedResponse(saved, metricsByWarehouseId.get(saved.getId()));
    }

    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = getWarehouse(id);

        warehouseRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã kho đã tồn tại");
                });

        warehouseMapper.updateEntity(request, warehouse);

        Warehouse saved = warehouseRepository.save(warehouse);
        Map<UUID, WarehouseMetrics> metricsByWarehouseId = loadMetrics(List.of(saved.getId()));
        return toEnrichedResponse(saved, metricsByWarehouseId.get(saved.getId()));
    }

    @Transactional
    public void delete(UUID id) {
        Warehouse warehouse = getWarehouse(id);
        warehouseRepository.delete(warehouse);
    }

    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

    private Map<UUID, WarehouseMetrics> loadMetrics(List<UUID> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, LocationRepository.WarehouseLocationStatsView> locationStats = locationRepository
                .getLocationStatsByWarehouseIds(warehouseIds)
                .stream()
                .collect(Collectors.toMap(
                        LocationRepository.WarehouseLocationStatsView::getWarehouseId,
                        value -> value));

        Map<UUID, Long> stockedBinsByWarehouse = stockLevelRepository.getStockedBinsByWarehouseIds(warehouseIds)
                .stream()
                .collect(Collectors.toMap(
                        StockLevelRepository.WarehouseStockedBinsView::getWarehouseId,
                        StockLevelRepository.WarehouseStockedBinsView::getStockedBins));

        Map<UUID, WarehouseMetrics> result = new HashMap<>();
        for (UUID warehouseId : warehouseIds) {
            LocationRepository.WarehouseLocationStatsView locationView = locationStats.get(warehouseId);
            int zonesCount = locationView == null ? 0 : locationView.getZonesCount().intValue();
            int binsCount = locationView == null ? 0 : locationView.getBinsCount().intValue();
            int stockedBins = stockedBinsByWarehouse.getOrDefault(warehouseId, 0L).intValue();

            result.put(warehouseId, new WarehouseMetrics(zonesCount, binsCount, stockedBins));
        }

        return result;
    }

    private WarehouseResponse toEnrichedResponse(Warehouse warehouse, WarehouseMetrics metrics) {
        WarehouseResponse base = warehouseMapper.toResponse(warehouse);
        WarehouseMetrics resolvedMetrics = metrics == null ? new WarehouseMetrics(0, 0, 0) : metrics;

        return new WarehouseResponse(
                base.id(),
                base.code(),
                base.name(),
                base.address(),
                base.managerName(),
                base.timezone(),
                base.isActive(),
                base.createdAt(),
                base.updatedAt(),
                resolvedMetrics.zonesCount(),
                resolvedMetrics.binsCount(),
                calculateFillRatePercent(resolvedMetrics));
    }

    private double calculateFillRatePercent(WarehouseMetrics metrics) {
        if (metrics.binsCount() <= 0) {
            return 0.0;
        }
        double value = (metrics.stockedBins() * 100.0) / metrics.binsCount();
        return Math.round(value * 100.0) / 100.0;
    }

    private record WarehouseMetrics(int zonesCount, int binsCount, int stockedBins) {
    }

}