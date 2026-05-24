package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.UUID;

public class StockLevelSpecification {

    public static Specification<StockLevel> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouse").get("id"), warehouseId);
    }

    public static Specification<StockLevel> warehouseIdIn(Collection<UUID> warehouseIds) {
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

    public static Specification<StockLevel> hasLocationId(UUID locationId) {
        return (root, query, cb) -> locationId == null ? null : cb.equal(root.get("location").get("id"), locationId);
    }

    public static Specification<StockLevel> hasProductId(UUID productId) {
        return (root, query, cb) -> productId == null ? null : cb.equal(root.get("productId"), productId);
    }

    public static Specification<StockLevel> hasKeyword(String keyword, Collection<UUID> productIds) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }

            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            var location = root.get("location");
            var warehouse = root.get("warehouse");

            var predicate = cb.or(
                    cb.like(cb.lower(root.get("lotNumber")), pattern),
                    cb.like(cb.lower(location.get("code")), pattern),
                    cb.like(cb.lower(location.get("zone")), pattern),
                    cb.like(cb.lower(location.get("aisle")), pattern),
                    cb.like(cb.lower(location.get("rack")), pattern),
                    cb.like(cb.lower(location.get("bin")), pattern),
                    cb.like(cb.lower(warehouse.get("code")), pattern),
                    cb.like(cb.lower(warehouse.get("name")), pattern)
            );

            if (productIds != null && !productIds.isEmpty()) {
                predicate = cb.or(predicate, root.get("productId").in(productIds));
            }

            return predicate;
        };
    }
}
