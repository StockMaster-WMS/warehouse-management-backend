package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
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
                    cb.like(cb.lower(root.get("customerName")), pattern)
            );
        };
    }

    public static Specification<SalesOrder> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            try {
                SalesOrderStatus enumStatus = SalesOrderStatus.valueOf(status.trim().toUpperCase());
                return cb.equal(root.get("status"), enumStatus);
            } catch (IllegalArgumentException e) {
                return cb.disjunction();
            }
        };
    }

    public static Specification<SalesOrder> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouseId"), warehouseId);
    }

    public static Specification<SalesOrder> createdFrom(OffsetDateTime createdFrom) {
        return (root, query, cb) -> createdFrom == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<SalesOrder> createdTo(OffsetDateTime createdTo) {
        return (root, query, cb) -> createdTo == null
                ? null
                : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }
}
