package com.warehouse_service.service;

import com.auth_service.entity.UserAccount;
import com.auth_service.repository.UserRepository;
import com.common.api.PagedResponse;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseManagerResponse;
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
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final LocationRepository locationRepository;
    private final WarehouseMapper warehouseMapper;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PagedResponse<WarehouseResponse> findAll(Pageable pageable, String keyword, Boolean isActive,
            String timezone, Collection<UUID> visibleWarehouseIds) {
        Specification<Warehouse> spec = WarehouseSpecification
                .idIn(visibleWarehouseIds)
                .and(WarehouseSpecification.hasKeyword(keyword))
                .and(WarehouseSpecification.hasActive(isActive))
                .and(WarehouseSpecification.hasTimezone(timezone));

        Page<Warehouse> page = warehouseRepository.findAll(spec, pageable);
        List<WarehouseResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public WarehouseSummaryResponse getSummary(Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds == null) {
            return new WarehouseSummaryResponse(
                    warehouseRepository.count(),
                    warehouseRepository.countByIsActiveTrue(),
                    warehouseRepository.countByIsActiveFalse(),
                    stockLevelRepository.countWarehousesWithStock());
        }
        if (visibleWarehouseIds.isEmpty()) {
            return new WarehouseSummaryResponse(0, 0, 0, 0);
        }
        return new WarehouseSummaryResponse(
                warehouseRepository.countByIdIn(visibleWarehouseIds),
                warehouseRepository.countByIdInAndIsActiveTrue(visibleWarehouseIds),
                warehouseRepository.countByIdInAndIsActiveFalse(visibleWarehouseIds),
                stockLevelRepository.countWarehousesWithStockByWarehouseIds(visibleWarehouseIds));
    }

    public WarehouseResponse findById(UUID id, Collection<UUID> visibleWarehouseIds) {
        Warehouse warehouse = getWarehouse(id);
        assertVisible(warehouse.getId(), visibleWarehouseIds);
        return toResponse(warehouse);
    }

    public WarehouseResponse findByCode(String code, Collection<UUID> visibleWarehouseIds) {
        Warehouse warehouse = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
        assertVisible(warehouse.getId(), visibleWarehouseIds);
        return toResponse(warehouse);
    }

    public List<WarehouseManagerResponse> findAvailableManagers() {
        return userRepository.findActiveByRoleCodes(List.of("WAREHOUSE_MANAGER")).stream()
                .sorted(Comparator.comparing(UserAccount::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toManagerResponse)
                .toList();
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        String code = normalizeCode(request.code());
        if (warehouseRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã kho đã tồn tại");
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);
        warehouse.setCode(code);
        applyManagers(warehouse, request.managerIds());
        Warehouse saved = warehouseRepository.save(warehouse);
        WarehouseResponse response = toResponse(saved);
        auditLogService.record("WAREHOUSE", "CREATE", "Tạo kho",
                "WAREHOUSE", saved.getId(), saved.getCode(), null, response, null, warehouseMetadata(saved));
        return response;
    }

    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = getWarehouse(id);
        WarehouseResponse before = toResponse(warehouse);
        String code = normalizeCode(request.code());

        warehouseRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã kho đã tồn tại");
                });

        warehouseMapper.updateEntity(request, warehouse);
        warehouse.setCode(code);
        applyManagers(warehouse, request.managerIds());
        Warehouse saved = warehouseRepository.save(warehouse);
        WarehouseResponse response = toResponse(saved);
        auditLogService.record("WAREHOUSE", "UPDATE", "Cập nhật kho",
                "WAREHOUSE", saved.getId(), saved.getCode(), before, response, null, warehouseMetadata(saved));
        return response;
    }

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

        WarehouseResponse before = toResponse(warehouse);
        warehouseRepository.delete(warehouse);
        auditLogService.record("WAREHOUSE", "DELETE", "Xóa kho",
                "WAREHOUSE", id, before.code(), before, null, null, Map.of("code", before.code()));
    }

    private Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));
    }

    private void assertVisible(UUID warehouseId, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null && !visibleWarehouseIds.contains(warehouseId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân công quản lý kho này");
        }
    }

    private void applyManagers(Warehouse warehouse, Set<UUID> managerIds) {
        Set<UserAccount> managers = resolveManagers(managerIds);
        warehouse.setManagers(managers);
        if (managers.isEmpty()) {
            warehouse.setManagerName(null);
            return;
        }
        warehouse.setManagerName(managers.stream()
                .map(user -> StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername())
                .collect(Collectors.joining(", ")));
    }

    private Set<UserAccount> resolveManagers(Set<UUID> managerIds) {
        if (managerIds == null || managerIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<UserAccount> managers = new LinkedHashSet<>();
        for (UUID managerId : managerIds) {
            UserAccount user = userRepository.findById(managerId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quản lý kho: " + managerId));
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Quản lý kho đang bị khóa: " + user.getUsername());
            }
            boolean isManager = user.getRoles() != null && user.getRoles().stream()
                    .anyMatch(role -> "WAREHOUSE_MANAGER".equalsIgnoreCase(role.getCode()));
            if (!isManager) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ được gán người dùng có role WAREHOUSE_MANAGER làm quản lý kho");
            }
            managers.add(user);
        }
        return managers;
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        List<WarehouseManagerResponse> managers = warehouse.getManagers() == null
                ? List.of()
                : warehouse.getManagers().stream()
                        .map(this::toManagerResponse)
                        .sorted(Comparator.comparing(WarehouseManagerResponse::fullName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                        .toList();
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getManagerName(),
                managers,
                warehouse.getTimezone(),
                warehouse.getIsActive(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt());
    }

    private WarehouseManagerResponse toManagerResponse(UserAccount user) {
        return new WarehouseManagerResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName());
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private Map<String, Object> warehouseMetadata(Warehouse warehouse) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("code", warehouse.getCode());
        metadata.put("name", warehouse.getName());
        metadata.put("managerIds", warehouse.getManagers() == null ? List.of()
                : warehouse.getManagers().stream().map(UserAccount::getId).toList());
        metadata.put("isActive", warehouse.getIsActive());
        return metadata;
    }
}
