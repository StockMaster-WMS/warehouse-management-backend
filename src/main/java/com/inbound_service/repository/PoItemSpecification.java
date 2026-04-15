package com.inbound_service.repository;

import com.inbound_service.entity.PoItem;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class PoItemSpecification {

    public static Specification<PoItem> hasPurchaseOrderId(UUID purchaseOrderId) {
        return (root, query, cb) -> purchaseOrderId == null
                ? null
                : cb.equal(root.get("purchaseOrder").get("id"), purchaseOrderId);
    }

    public static Specification<PoItem> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("productSku")), pattern);
        };
    }
}
