package com.inbound_service.repository;

import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public class PurchaseOrderSpecification {

    public static Specification<PurchaseOrder> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("poNumber")), pattern);
        };
    }

    public static Specification<PurchaseOrder> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            try {
                PurchaseOrderStatus enumStatus = PurchaseOrderStatus.valueOf(status.trim().toUpperCase());
                return cb.equal(root.get("status"), enumStatus);
            } catch (IllegalArgumentException e) {
                return cb.disjunction();
            }
        };
    }

    public static Specification<PurchaseOrder> hasSupplierId(UUID supplierId) {
        return (root, query, cb) -> supplierId == null ? null : cb.equal(root.get("supplierId"), supplierId);
    }

    public static Specification<PurchaseOrder> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouseId"), warehouseId);
    }

    public static Specification<PurchaseOrder> warehouseIdIn(Collection<UUID> warehouseIds) {
        return (root, query, cb) -> {
            if (warehouseIds == null) {
                return null;
            }
            if (warehouseIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("warehouseId").in(warehouseIds);
        };
    }

    public static Specification<PurchaseOrder> createdFrom(OffsetDateTime createdFrom) {
        return (root, query, cb) -> createdFrom == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<PurchaseOrder> createdTo(OffsetDateTime createdTo) {
        return (root, query, cb) -> createdTo == null
                ? null
                : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }
}
