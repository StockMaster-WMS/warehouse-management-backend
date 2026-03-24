package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrderItem;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class SalesOrderItemSpecification {

    public static Specification<SalesOrderItem> hasSalesOrderId(UUID salesOrderId) {
        return (root, query, cb) -> salesOrderId == null ? null : cb.equal(root.get("salesOrder").get("id"), salesOrderId);
    }

    public static Specification<SalesOrderItem> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("productSku")), pattern);
        };
    }
}
