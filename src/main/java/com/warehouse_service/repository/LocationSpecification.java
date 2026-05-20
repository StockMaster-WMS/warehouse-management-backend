package com.warehouse_service.repository;

import com.warehouse_service.entity.Location;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.UUID;

public class LocationSpecification {

    public static Specification<Location> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouse").get("id"), warehouseId);
    }

    public static Specification<Location> warehouseIdIn(Collection<UUID> warehouseIds) {
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

    public static Specification<Location> hasZone(String zone) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(zone)) {
                return null;
            }
            return cb.equal(cb.lower(root.get("zone")), zone.toLowerCase());
        };
    }

    public static Specification<Location> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("zone")), pattern),
                    cb.like(cb.lower(root.get("aisle")), pattern)
            );
        };
    }
}
