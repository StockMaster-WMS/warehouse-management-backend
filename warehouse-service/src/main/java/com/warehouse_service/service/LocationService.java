package com.warehouse_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateLocationRequest;
import com.warehouse_service.dto.request.UpdateLocationRequest;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.LocationMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationMapper locationMapper;

    public List<LocationResponse> findAll(UUID warehouseId, String zone) {
        List<Location> locations = warehouseId == null
                ? locationRepository.findAll()
                : locationRepository.findByWarehouseId(warehouseId);

        return locations.stream()
                .filter(location -> zone == null || zone.isBlank() || zone.equalsIgnoreCase(location.getZone()))
                .map(locationMapper::toResponse)
                .toList();
    }

    public LocationResponse findById(UUID id) {
        return locationMapper.toResponse(getLocation(id));
    }

    @Transactional
    public LocationResponse create(CreateLocationRequest request) {
        Warehouse warehouse = getWarehouse(request.warehouseId());
        ensureCodeUnique(request.warehouseId(), request.code(), null);

        Location location = locationMapper.toEntity(request);
        location.setWarehouse(warehouse);

        return locationMapper.toResponse(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse update(UUID id, UpdateLocationRequest request) {
        Location location = getLocation(id);
        Warehouse warehouse = getWarehouse(request.warehouseId());

        ensureCodeUnique(request.warehouseId(), request.code(), id);

        locationMapper.updateEntity(request, location);
        location.setWarehouse(warehouse);

        return locationMapper.toResponse(locationRepository.save(location));
    }

    @Transactional
    public void delete(UUID id) {
        Location location = getLocation(id);
        locationRepository.delete(location);
    }

    private Location getLocation(UUID id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí"));
    }

    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

    private void ensureCodeUnique(UUID warehouseId, String code, UUID currentLocationId) {
        locationRepository.findByWarehouseIdAndCode(warehouseId, code)
                .filter(existing -> currentLocationId == null || !existing.getId().equals(currentLocationId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã vị trí đã tồn tại trong kho");
                });
    }
}