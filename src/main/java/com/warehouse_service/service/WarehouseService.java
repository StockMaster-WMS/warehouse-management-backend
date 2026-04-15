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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final LocationRepository locationRepository;
    private final WarehouseMapper warehouseMapper;

    // Lấy danh sách kho có phân trang và bộ lọc.
    public PagedResponse<WarehouseResponse> findAll(Pageable pageable, String keyword, Boolean isActive,
            String timezone) {
        Specification<Warehouse> spec = WarehouseSpecification
                .hasKeyword(keyword)
                .and(WarehouseSpecification.hasActive(isActive))
                .and(WarehouseSpecification.hasTimezone(timezone));

        Page<Warehouse> page = warehouseRepository.findAll(spec, pageable);
        List<WarehouseResponse> content = page.getContent().stream()
                .map(warehouseMapper::toResponse)
                .toList();

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    // Lấy số liệu tổng quan của hệ thống kho.
    public WarehouseSummaryResponse getSummary() {
        long totalWarehouses = warehouseRepository.count();
        long activeWarehouses = warehouseRepository.countByIsActiveTrue();
        long inactiveWarehouses = warehouseRepository.countByIsActiveFalse();
        long warehousesWithStock = stockLevelRepository.countWarehousesWithStock();

        return new WarehouseSummaryResponse(
                totalWarehouses,
                activeWarehouses,
                inactiveWarehouses,
                warehousesWithStock);
    }

    // Lấy chi tiết kho theo id.
    public WarehouseResponse findById(UUID id) {
        return warehouseMapper.toResponse(getWarehouse(id));
    }

    // Lấy chi tiết kho theo mã kho.
    public WarehouseResponse findByCode(String code) {
        Warehouse warehouse = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
        return warehouseMapper.toResponse(warehouse);
    }

    // Tạo mới kho.
    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã kho đã tồn tại");
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);
        Warehouse saved = warehouseRepository.save(warehouse);
        return warehouseMapper.toResponse(saved);
    }

    // Cập nhật thông tin kho theo id.
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
        return warehouseMapper.toResponse(saved);
    }

    // Xóa kho theo id (kiểm tra ràng buộc trước khi xóa).
    @Transactional
    public void delete(UUID id) {
        Warehouse warehouse = getWarehouse(id);

        if (stockLevelRepository.existsByWarehouseId(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể xóa kho đang có tồn kho. Vui lòng chuyển hoặc xóa tồn kho trước");
        }
        if (locationRepository.existsByWarehouseId(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể xóa kho đang có vị trí. Vui lòng xóa vị trí trước");
        }

        warehouseRepository.delete(warehouse);
    }

    // Tìm thực thể kho, ném lỗi nếu không tồn tại.
    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

}
