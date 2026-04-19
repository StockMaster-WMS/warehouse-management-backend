package com.warehouse_service.repository;

import com.warehouse_service.entity.Warehouse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class WarehouseSpecification {

    public static Specification<Warehouse> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }

            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern)
            );
        };
    }

    public static Specification<Warehouse> hasActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("isActive"), isActive);
    }

    public static Specification<Warehouse> hasTimezone(String timezone) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(timezone)) {
                return null;
            }
            return cb.equal(cb.lower(root.get("timezone")), timezone.toLowerCase());
        };
    }
}
