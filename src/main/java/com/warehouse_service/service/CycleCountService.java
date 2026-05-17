package com.warehouse_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.dto.request.CreateCycleCountRequest;
import com.warehouse_service.dto.request.RecordCountRequest;
import com.warehouse_service.dto.response.CycleCountResponse;
import com.warehouse_service.entity.CycleCount;
import com.warehouse_service.entity.CycleCountItem;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.repository.CycleCountItemRepository;
import com.warehouse_service.repository.CycleCountRepository;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final CycleCountItemRepository cycleCountItemRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockLevelService stockLevelService;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;

    public List<CycleCountResponse> getAll() {
        return cycleCountRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CycleCountResponse getById(UUID id) {
        return cycleCountRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt kiểm kê"));
    }

    @Transactional
    public CycleCountResponse create(CreateCycleCountRequest request, UUID creatorId) {
        warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho"));

        Set<String> uniqueLines = new HashSet<>();
        CycleCount count = CycleCount.builder()
                .warehouseId(request.warehouseId())
                .description(request.description())
                .scheduledAt(request.scheduledAt() != null ? request.scheduledAt() : OffsetDateTime.now())
                .status(CycleCount.CycleCountStatus.PENDING)
                .createdBy(creatorId)
                .build();

        CycleCount saved = cycleCountRepository.save(count);

        List<CycleCountItem> items = request.items().stream().map(req -> {
            Location location = locationRepository.findById(req.locationId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí"));
            if (!location.getWarehouse().getId().equals(request.warehouseId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí kiểm kê không thuộc kho đã chọn");
            }

            String lot = normalizeLot(req.lotNumber());
            String lineKey = req.locationId() + ":" + req.productId() + ":" + lot;
            if (!uniqueLines.add(lineKey)) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng kiểm kê bị trùng vị trí/sản phẩm/lô: " + lineKey);
            }

            Integer systemQty = stockLevelRepository.findByLocationIdAndProductIdAndLotNumber(
                            req.locationId(), req.productId(), lot)
                    .map(StockLevel::getQtyOnHand)
                    .orElse(0);

            return CycleCountItem.builder()
                    .cycleCount(saved)
                    .productId(req.productId())
                    .locationId(req.locationId())
                    .lotNumber(lot)
                    .systemQty(systemQty)
                    .status(CycleCountItem.ItemStatus.PENDING)
                    .build();
        }).collect(Collectors.toList());

        cycleCountItemRepository.saveAll(items);
        saved.setItems(items);

        return toResponse(saved);
    }

    @Transactional
    public CycleCountResponse startCounting(UUID id) {
        CycleCount count = getEntity(id);
        if (count.getStatus() != CycleCount.CycleCountStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ bắt đầu khi đợt kiểm kê đang PENDING");
        }
        count.setStatus(CycleCount.CycleCountStatus.IN_PROGRESS);
        return toResponse(cycleCountRepository.save(count));
    }

    @Transactional
    public CycleCountResponse recordCount(UUID countId, RecordCountRequest request) {
        CycleCount count = cycleCountRepository.findByIdWithItemsForUpdate(countId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt kiểm kê"));
        if (count.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ ghi nhận số lượng khi đợt kiểm kê đang IN_PROGRESS");
        }
        if (request.countedQty() < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng đếm được không được âm");
        }

        CycleCountItem item = count.getItems().stream()
                .filter(line -> line.getId().equals(request.itemId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng kiểm kê"));

        item.setCountedQty(request.countedQty());
        item.setDiscrepancy(request.countedQty() - item.getSystemQty());
        item.setNotes(request.notes());
        item.setStatus(CycleCountItem.ItemStatus.COUNTED);

        cycleCountItemRepository.save(item);
        return toResponse(count);
    }

    @Transactional
    public CycleCountResponse completeAndAdjust(UUID id, UUID approverId) {
        CycleCount count = cycleCountRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt kiểm kê"));
        if (count.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ hoàn tất khi đang IN_PROGRESS");
        }

        for (CycleCountItem item : count.getItems()) {
            if (item.getStatus() == CycleCountItem.ItemStatus.COUNTED && item.getDiscrepancy() != 0) {
                // Adjust stock
                StockAdjustCommand cmd = new StockAdjustCommand(
                        count.getWarehouseId(),
                        item.getLocationId(),
                        item.getProductId(),
                        normalizeLot(item.getLotNumber()),
                        item.getDiscrepancy(),
                        "CYCLE_COUNT:" + count.getId() + ":" + item.getId(),
                        "CYCLE_COUNT",
                        count.getId()
                );
                stockLevelService.adjust(cmd);
                item.setStatus(CycleCountItem.ItemStatus.ADJUSTED);
            } else if (item.getStatus() == CycleCountItem.ItemStatus.PENDING) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Vẫn còn dòng chưa được đếm số lượng");
            }
        }

        count.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        count.setCompletedAt(OffsetDateTime.now());
        count.setApprovedBy(approverId);

        return toResponse(cycleCountRepository.save(count));
    }

    private CycleCount getEntity(UUID id) {
        return cycleCountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt kiểm kê"));
    }

    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private CycleCountResponse toResponse(CycleCount count) {
        List<CycleCountResponse.ItemResponse> items = count.getItems() == null ? List.of() :
                count.getItems().stream().map(item -> new CycleCountResponse.ItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getLocationId(),
                        item.getLotNumber(),
                        item.getSystemQty(),
                        item.getCountedQty(),
                        item.getDiscrepancy(),
                        item.getStatus().name(),
                        item.getNotes()
                )).collect(Collectors.toList());

        return new CycleCountResponse(
                count.getId(),
                count.getWarehouseId(),
                count.getStatus().name(),
                count.getDescription(),
                count.getScheduledAt(),
                count.getCompletedAt(),
                count.getCreatedBy(),
                count.getApprovedBy(),
                count.getCreatedAt(),
                items
        );
    }
}
