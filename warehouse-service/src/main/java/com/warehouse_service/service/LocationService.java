package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateLocationRequest;
import com.warehouse_service.dto.request.UpdateLocationRequest;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.LocationMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.LocationSpecification;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationMapper locationMapper;

    // Lấy danh sách vị trí có phân trang và bộ lọc.
    public PagedResponse<LocationResponse> findAll(Pageable pageable, UUID warehouseId, String zone, String keyword) {
        Specification<Location> spec = LocationSpecification.hasWarehouseId(warehouseId)
                .and(LocationSpecification.hasZone(zone))
                .and(LocationSpecification.hasKeyword(keyword));
        Page<Location> page = locationRepository.findAll(spec, pageable);
        Page<LocationResponse> mapped = page.map(locationMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết vị trí theo id.
    public LocationResponse findById(UUID id) {
        return locationMapper.toResponse(getLocation(id));
    }

    // Tạo mới vị trí trong kho.
    @Transactional
    public LocationResponse create(CreateLocationRequest request) {
        Warehouse warehouse = getWarehouse(request.warehouseId());
        ensureCodeUnique(request.warehouseId(), request.code(), null);

        Location location = locationMapper.toEntity(request);
        location.setWarehouse(warehouse);

        return locationMapper.toResponse(locationRepository.save(location));
    }

    // Cập nhật thông tin vị trí theo id.
    @Transactional
    public LocationResponse update(UUID id, UpdateLocationRequest request) {
        Location location = getLocation(id);
        Warehouse warehouse = getWarehouse(request.warehouseId());

        ensureCodeUnique(request.warehouseId(), request.code(), id);

        locationMapper.updateEntity(request, location);
        location.setWarehouse(warehouse);

        return locationMapper.toResponse(locationRepository.save(location));
    }

    // Xóa vị trí theo id.
    @Transactional
    public void delete(UUID id) {
        Location location = getLocation(id);
        locationRepository.delete(location);
    }

    // Tìm thực thể vị trí, ném lỗi nếu không tồn tại.
    private Location getLocation(UUID id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí"));
    }

    // Tìm thực thể kho, ném lỗi nếu không tồn tại.
    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

    // Kiểm tra mã vị trí không trùng trong cùng một kho.
    private void ensureCodeUnique(UUID warehouseId, String code, UUID currentLocationId) {
        locationRepository.findByWarehouseIdAndCode(warehouseId, code)
                .filter(existing -> currentLocationId == null || !existing.getId().equals(currentLocationId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã vị trí đã tồn tại trong kho");
                });
    }
}