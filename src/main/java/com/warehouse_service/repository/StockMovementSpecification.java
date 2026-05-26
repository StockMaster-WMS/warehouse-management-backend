package com.warehouse_service.repository;

import com.warehouse_service.entity.StockMovement;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public class StockMovementSpecification {

    private StockMovementSpecification() {
    }

    public static Specification<StockMovement> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null
                : cb.equal(root.get("warehouse").get("id"), warehouseId);
    }

    public static Specification<StockMovement> warehouseIdIn(Collection<UUID> warehouseIds) {
        return (root, query, cb) -> {
            if (warehouseIds == null) {
                return null;
            }
            if (warehouseIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("warehouse").get("id").in(warehouseIds);
        };
    }

    public static Specification<StockMovement> hasLocationId(UUID locationId) {
        return (root, query, cb) -> locationId == null ? null
                : cb.equal(root.get("location").get("id"), locationId);
    }

    public static Specification<StockMovement> hasProductId(UUID productId) {
        return (root, query, cb) -> productId == null ? null
                : cb.equal(root.get("productId"), productId);
    }

    public static Specification<StockMovement> hasMovementType(String movementType) {
        return (root, query, cb) -> movementType == null || movementType.isBlank() ? null
                : cb.equal(root.get("movementType"), movementType.toUpperCase());
    }

    public static Specification<StockMovement> excludeMovementType(String movementType) {
        return (root, query, cb) -> movementType == null || movementType.isBlank() ? null
                : cb.notEqual(root.get("movementType"), movementType.toUpperCase());
    }

    public static Specification<StockMovement> createdAfter(OffsetDateTime from) {
        return (root, query, cb) -> from == null ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<StockMovement> createdBefore(OffsetDateTime to) {
        return (root, query, cb) -> to == null ? null
                : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
