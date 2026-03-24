package com.inbound_service.repository;

import com.inbound_service.entity.PurchaseOrder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class PurchaseOrderSpecification {

    public static Specification<PurchaseOrder> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("poNumber")), pattern),
                    cb.like(cb.lower(root.get("status")), pattern)
            );
        };
    }

    public static Specification<PurchaseOrder> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<PurchaseOrder> hasSupplierId(UUID supplierId) {
        return (root, query, cb) -> supplierId == null ? null : cb.equal(root.get("supplierId"), supplierId);
    }

    public static Specification<PurchaseOrder> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouseId"), warehouseId);
    }
}
