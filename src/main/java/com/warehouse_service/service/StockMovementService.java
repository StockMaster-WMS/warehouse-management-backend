package com.warehouse_service.service;

import com.common.api.PagedResponse;
import com.warehouse_service.dto.response.StockMovementResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.StockMovement;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.StockMovementRepository;
import com.warehouse_service.repository.StockMovementSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;

    // Lấy lịch sử biến động tồn kho với phân trang và bộ lọc.
    public PagedResponse<StockMovementResponse> findAll(Pageable pageable,
            UUID warehouseId, UUID locationId, UUID productId,
            String movementType, OffsetDateTime from, OffsetDateTime to) {
        Specification<StockMovement> spec = StockMovementSpecification.hasWarehouseId(warehouseId)
                .and(StockMovementSpecification.hasLocationId(locationId))
                .and(StockMovementSpecification.hasProductId(productId))
                .and(StockMovementSpecification.hasMovementType(movementType))
                .and(StockMovementSpecification.createdAfter(from))
                .and(StockMovementSpecification.createdBefore(to));

        Page<StockMovement> page = stockMovementRepository.findAll(spec, pageable);
        List<StockMovementResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    // Ghi log biến động tồn kho khi adjust qty.
    @Transactional
    public void recordAdjustment(StockLevel stock, int qtyDelta, String movementType,
            String reason, String referenceType, UUID referenceId) {
        StockMovement movement = StockMovement.builder()
                .warehouse(stock.getWarehouse())
                .location(stock.getLocation())
                .productId(stock.getProductId())
                .lotNumber(stock.getLotNumber())
                .movementType(movementType)
                .qtyChange(qtyDelta)
                .qtyAfter(stock.getQtyOnHand())
                .reservedChange(0)
                .reservedAfter(stock.getQtyReserved() == null ? 0 : stock.getQtyReserved())
                .reason(reason)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
        stockMovementRepository.save(movement);
    }

    // Ghi log biến động reserved khi adjust reserved.
    @Transactional
    public void recordReservedAdjustment(StockLevel stock, int reservedDelta, String movementType,
            String reason, String referenceType, UUID referenceId) {
        StockMovement movement = StockMovement.builder()
                .warehouse(stock.getWarehouse())
                .location(stock.getLocation())
                .productId(stock.getProductId())
                .lotNumber(stock.getLotNumber())
                .movementType(movementType)
                .qtyChange(0)
                .qtyAfter(stock.getQtyOnHand())
                .reservedChange(reservedDelta)
                .reservedAfter(stock.getQtyReserved() == null ? 0 : stock.getQtyReserved())
                .reason(reason)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
        stockMovementRepository.save(movement);
    }

    private StockMovementResponse toResponse(StockMovement m) {
        Warehouse w = m.getWarehouse();
        Location l = m.getLocation();
        return new StockMovementResponse(
                m.getId(),
                w != null ? w.getId() : null,
                w != null ? w.getCode() : null,
                l != null ? l.getId() : null,
                l != null ? l.getCode() : null,
                m.getProductId(),
                m.getLotNumber(),
                m.getMovementType(),
                m.getQtyChange(),
                m.getQtyAfter(),
                m.getReservedChange(),
                m.getReservedAfter(),
                m.getReason(),
                m.getReferenceType(),
                m.getReferenceId(),
                m.getCreatedBy(),
                m.getCreatedAt()
        );
    }
}
