package com.warehouse_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public List<WarehouseResponse> findAll() {
        return warehouseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public WarehouseResponse findById(UUID id) {
        return toResponse(getWarehouse(id));
    }

    public WarehouseResponse findByCode(String code) {
        return toResponse(warehouseRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Warehouse not found")));
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Warehouse code already exists");
        }

        Warehouse warehouse = Warehouse.builder()
                .code(request.code())
                .build();

        applyRequest(warehouse, request.name(), request.address(), request.timezone(), request.isActive());

        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = getWarehouse(id);

        warehouseRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Warehouse code already exists");
                });

        warehouse.setCode(request.code());
        applyRequest(warehouse, request.name(), request.address(), request.timezone(), request.isActive());

        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public void delete(UUID id) {
        Warehouse warehouse = getWarehouse(id);
        warehouseRepository.delete(warehouse);
    }

    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Warehouse not found"));
    }

    private void applyRequest(Warehouse warehouse,
                              String name,
                              String address,
                              String timezone,
                              Boolean isActive) {
        warehouse.setName(name);
        warehouse.setAddress(address);
        warehouse.setTimezone(timezone == null || timezone.isBlank() ? "Asia/Ho_Chi_Minh" : timezone);
        warehouse.setIsActive(isActive == null || isActive);
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getTimezone(),
                warehouse.getIsActive(),
                warehouse.getCreatedAt()
        );
    }
}