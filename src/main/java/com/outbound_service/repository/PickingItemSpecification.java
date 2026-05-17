package com.outbound_service.repository;

import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
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

    public static Specification<PickingItem> hasStatus(PickingItemStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<PickingItem> salesOrderCreatedFrom(OffsetDateTime createdFrom) {
        return (root, query, cb) -> {
            if (createdFrom == null) {
                return null;
            }
            Join<Object, Object> soItem = root.join("soItem");
            return cb.greaterThanOrEqualTo(soItem.get("salesOrder").get("createdAt"), createdFrom);
        };
    }

    public static Specification<PickingItem> salesOrderCreatedTo(OffsetDateTime createdTo) {
        return (root, query, cb) -> {
            if (createdTo == null) {
                return null;
            }
            Join<Object, Object> soItem = root.join("soItem");
            return cb.lessThanOrEqualTo(soItem.get("salesOrder").get("createdAt"), createdTo);
        };
    }
}
