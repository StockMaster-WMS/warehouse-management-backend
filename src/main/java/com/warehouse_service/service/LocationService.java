package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.BulkLocationGeneratorRequest;
import com.warehouse_service.dto.request.CreateLocationRequest;
import com.warehouse_service.dto.request.UpdateLocationRequest;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.LocationMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.LocationSpecification;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private static final int MAX_BULK_LOCATIONS = 5_000;

    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final LocationMapper locationMapper;

    // Lấy danh sách vị trí có phân trang và bộ lọc.
    public PagedResponse<LocationResponse> findAll(Pageable pageable, UUID warehouseId, String zone, String keyword) {
        return findAll(pageable, warehouseId, zone, null, keyword, null);
    }

    public PagedResponse<LocationResponse> findAll(Pageable pageable, UUID warehouseId, String zone, String keyword,
            Collection<UUID> visibleWarehouseIds) {
        return findAll(pageable, warehouseId, zone, null, keyword, visibleWarehouseIds);
    }

    public PagedResponse<LocationResponse> findAll(Pageable pageable, UUID warehouseId, String zone, String locationType,
            String keyword, Collection<UUID> visibleWarehouseIds) {
        Specification<Location> spec = LocationSpecification.warehouseIdIn(visibleWarehouseIds)
                .and(LocationSpecification.hasWarehouseId(warehouseId))
                .and(LocationSpecification.hasZone(zone))
                .and(LocationSpecification.hasLocationType(locationType))
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

    public LocationResponse findById(UUID id, Collection<UUID> visibleWarehouseIds) {
        Location location = getLocation(id);
        if (visibleWarehouseIds != null && !visibleWarehouseIds.contains(location.getWarehouse().getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân công quản lý kho của vị trí này");
        }
        return locationMapper.toResponse(location);
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

    // Tạo hàng loạt vị trí theo quy luật.
    @Transactional
    public void generateBulk(BulkLocationGeneratorRequest request) {
        Warehouse warehouse = getWarehouse(request.getWarehouseId());
        long total = 1L * request.getAisleCount()
                * request.getRackCount()
                * request.getLevelCount()
                * request.getBinCount();
        if (total > MAX_BULK_LOCATIONS) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Số vị trí tạo hàng loạt vượt giới hạn " + MAX_BULK_LOCATIONS);
        }
        List<Location> locations = new ArrayList<>();
        String warehouseCodePrefix = normalizeCodeSegment(request.getWarehouseCodePrefix());
        String areaCode = normalizeCodeSegment(request.getAreaCode());
        if (warehouseCodePrefix.isBlank() || areaCode.isBlank()) {
            String[] inferredPrefix = inferLocationPrefix(warehouse.getCode());
            if (warehouseCodePrefix.isBlank()) {
                warehouseCodePrefix = inferredPrefix[0];
            }
            if (areaCode.isBlank()) {
                areaCode = inferredPrefix[1];
            }
        }
        String zone = normalizeCodeSegment(request.getZone());
        String aislePrefix = normalizeCodeSegment(request.getAislePrefix());
        String rackPrefix = normalizeCodeSegment(request.getRackPrefix());
        String binPrefix = normalizeCodeSegment(request.getBinPrefix());
        int aisleStart = request.getAisleStart() != null ? request.getAisleStart() : 1;
        int rackStart = request.getRackStart() != null ? request.getRackStart() : 1;
        int levelStart = request.getLevelStart() != null ? request.getLevelStart() : 1;
        int binStart = request.getBinStart() != null ? request.getBinStart() : 1;

        for (int a = aisleStart; a < aisleStart + request.getAisleCount(); a++) {
            String aisle = String.format("%s%02d", aislePrefix, a);

            for (int r = rackStart; r < rackStart + request.getRackCount(); r++) {
                String rack = String.format("%s%02d", rackPrefix, r);

                for (int l = levelStart; l < levelStart + request.getLevelCount(); l++) {
                    short level = (short) l;

                    for (int b = binStart; b < binStart + request.getBinCount(); b++) {
                        String bin = String.format("%s%02d", binPrefix, b);

                        // Quy luật mã: WAREHOUSE-AREA-ZONE-AISLE-RACK-L(Level)-B(Bin)
                        String code = String.format("%s-%s-%s-%s-%s-L%02d-%s",
                                warehouseCodePrefix, areaCode, zone, aisle, rack, level, bin);

                        // Tự động gán các thuộc tính vùng dựa trên tên vùng chọn từ FE
                        boolean isCold = "COLD".equalsIgnoreCase(zone);
                        boolean isHeavy = "HEAVY".equalsIgnoreCase(zone);
                        boolean isHazmat = "HAZMAT".equalsIgnoreCase(zone) || "HAZ".equalsIgnoreCase(zone);
                        
                        // Gán độ ưu tiên lấy hàng dựa trên khoảng cách dock: FAST gần nhất (nhỏ nhất)
                        int pickSeq = switch (zone.toUpperCase(Locale.ROOT)) {
                            case "PICK", "FAST" -> 10;
                            case "HEAVY" -> 20;
                            case "BULK" -> 30;
                            case "COLD" -> 50;
                            case "HAZ", "HAZMAT" -> 80;
                            default -> 100;
                        };

                        Location location = Location.builder()
                                .warehouse(warehouse)
                                .zone(zone)
                                .aisle(aisle)
                                .rack(rack)
                                .level(level)
                                .bin(bin)
                                .code(code)
                                .locationType(request.getLocationType())
                                .status("AVAILABLE")
                                .isColdZone(isCold)
                                .isHeavyZone(isHeavy)
                                .isHazmatZone(isHazmat)
                                .pickSequence(pickSeq)
                                .isActive(true)
                                .build();

                        locations.add(location);
                    }
                }
            }
        }
        // Lưu toàn bộ danh sách (JPA sẽ tự tối ưu batch insert nếu cấu hình hibernate.jdbc.batch_size)
        locationRepository.saveAll(locations);
    }

    private String normalizeCodeSegment(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String[] inferLocationPrefix(String warehouseCode) {
        String normalized = warehouseCode == null ? "" : warehouseCode.trim().toUpperCase(Locale.ROOT);
        String[] parts = normalized.split("-");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            String segment = normalizeCodeSegment(part);
            if (!segment.isBlank() && !"WH".equals(segment)) {
                segments.add(segment);
            }
        }
        String warehouse = segments.isEmpty() ? "WH" : segments.get(0);
        String area = segments.size() > 1 ? segments.get(1) : "TT";
        return new String[] { warehouse, area };
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
        if (stockLevelRepository.existsByLocationId(id)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể xóa vị trí đang có tồn kho. Vui lòng chuyển hoặc xóa tồn kho trước");
        }
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
