package com.product_service.repository;

import com.product_service.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class ProductSpecification {

    public static Specification<Product> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null; // Bỏ qua nếu không có keyword
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("productName")), pattern),
                    cb.like(cb.lower(root.get("sku")), pattern)
            );
        };
    }

    public static Specification<Product> hasCategory(UUID categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) {
                return null; // Bỏ qua nếu không có categoryId
            }
            return cb.equal(root.get("category").get("id"), categoryId);
        };
    }

    public static Specification<Product> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null; // Bỏ qua nếu không có status
            }
            return cb.equal(root.get("status"), status);
        };
    }
}
