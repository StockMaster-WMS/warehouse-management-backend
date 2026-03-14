package com.warehouse_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.WarehouseMapper;
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
    private final WarehouseMapper warehouseMapper;

    public List<WarehouseResponse> findAll() {
        return warehouseRepository.findAll()
                .stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    public WarehouseResponse findById(UUID id) {
        return warehouseMapper.toResponse(getWarehouse(id));
    }

    public WarehouseResponse findByCode(String code) {
        return warehouseMapper.toResponse(warehouseRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho")));
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã kho đã tồn tại");
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);

        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
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

        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
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

}