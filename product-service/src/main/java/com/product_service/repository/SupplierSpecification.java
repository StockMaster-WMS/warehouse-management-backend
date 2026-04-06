package com.product_service.repository;

import com.product_service.entity.Supplier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class SupplierSpecification {

    public static Specification<Supplier> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("taxCode")), pattern),
                    cb.like(cb.lower(root.get("contactName")), pattern)
            );
        };
    }

    public static Specification<Supplier> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            return cb.equal(cb.lower(root.get("status")), status.toLowerCase());
        };
    }
}
