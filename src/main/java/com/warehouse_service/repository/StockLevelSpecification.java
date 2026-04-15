package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class StockLevelSpecification {

    public static Specification<StockLevel> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouse").get("id"), warehouseId);
    }

    public static Specification<StockLevel> hasLocationId(UUID locationId) {
        return (root, query, cb) -> locationId == null ? null : cb.equal(root.get("location").get("id"), locationId);
    }

    public static Specification<StockLevel> hasProductId(UUID productId) {
        return (root, query, cb) -> productId == null ? null : cb.equal(root.get("productId"), productId);
    }
}
