package com.warehouse_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
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

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        Warehouse warehouse = Warehouse.builder()
                .code(request.code())
                .name(request.name())
                .address(request.address())
                .timezone(request.timezone() == null || request.timezone().isBlank() ? "Asia/Ho_Chi_Minh" : request.timezone())
                .isActive(request.isActive() == null || request.isActive())
                .build();

        return toResponse(warehouseRepository.save(warehouse));
    }

    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Warehouse not found"));
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