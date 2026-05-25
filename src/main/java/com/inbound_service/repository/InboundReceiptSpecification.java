package com.inbound_service.repository;

import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public class InboundReceiptSpecification {

    public static Specification<InboundReceipt> hasPurchaseOrderId(UUID purchaseOrderId) {
        return (root, query, cb) -> purchaseOrderId == null
                ? null
                : cb.equal(root.get("purchaseOrder").get("id"), purchaseOrderId);
    }

    public static Specification<InboundReceipt> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("receiptNumber")), pattern);
        };
    }

    public static Specification<InboundReceipt> hasWarehouseId(UUID warehouseId) {
        return (root, query, cb) -> warehouseId == null
                ? null
                : cb.equal(root.get("warehouseId"), warehouseId);
    }

    public static Specification<InboundReceipt> warehouseIdIn(Collection<UUID> warehouseIds) {
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

    public static Specification<InboundReceipt> hasStatus(InboundReceiptStatus status) {
        return (root, query, cb) -> status == null
                ? null
                : cb.equal(root.get("status"), status);
    }

    public static Specification<InboundReceipt> createdFrom(OffsetDateTime createdFrom) {
        return (root, query, cb) -> createdFrom == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<InboundReceipt> createdTo(OffsetDateTime createdTo) {
        return (root, query, cb) -> createdTo == null
                ? null
                : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }
}
