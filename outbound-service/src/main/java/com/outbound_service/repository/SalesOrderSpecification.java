package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class SalesOrderSpecification {

    public static Specification<SalesOrder> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("soNumber")), pattern),
                    cb.like(cb.lower(root.get("customerName")), pattern),
                    cb.like(cb.lower(root.get("status")), pattern)
            );
        };
    }

    public static Specification<SalesOrder> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<SalesOrder> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouseId"), warehouseId);
    }
}
