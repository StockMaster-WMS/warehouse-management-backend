package com.inbound_service.repository;

import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

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

    public static Specification<InboundReceipt> hasStatus(InboundReceiptStatus status) {
        return (root, query, cb) -> status == null
                ? null
                : cb.equal(root.get("status"), status);
    }
}
