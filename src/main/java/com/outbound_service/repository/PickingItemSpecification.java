package com.outbound_service.repository;

import com.outbound_service.entity.PickingItem;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class PickingItemSpecification {

    public static Specification<PickingItem> hasSoItemId(UUID soItemId) {
        return (root, query, cb) -> soItemId == null ? null : cb.equal(root.join("soItem").get("id"), soItemId);
    }

    public static Specification<PickingItem> hasProductId(UUID productId) {
        return (root, query, cb) -> productId == null ? null : cb.equal(root.get("productId"), productId);
    }

    public static Specification<PickingItem> hasLocationId(UUID locationId) {
        return (root, query, cb) -> locationId == null ? null : cb.equal(root.get("locationId"), locationId);
    }

    public static Specification<PickingItem> hasStatus(String status) {
        return (root, query, cb) -> (status == null || status.isBlank()) 
            ? null 
            : cb.equal(root.get("status"), com.outbound_service.entity.PickingItemStatus.valueOf(status.toUpperCase()));
    }
}
