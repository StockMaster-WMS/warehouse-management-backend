package com.inbound_service.repository;

import com.inbound_service.entity.PutawayStatus;
import com.inbound_service.entity.PutawayTask;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.UUID;

public class PutawayTaskSpecification {

    public static Specification<PutawayTask> hasPoItemId(UUID poItemId) {
        return (root, query, cb) -> poItemId == null ? null : cb.equal(root.join("poItem").get("id"), poItemId);
    }

    public static Specification<PutawayTask> hasAssignedTo(UUID assignedTo) {
        return (root, query, cb) -> assignedTo == null ? null : cb.equal(root.get("assignedTo"), assignedTo);
    }

    public static Specification<PutawayTask> hasWarehouseIds(Collection<UUID> warehouseIds) {
        return (root, query, cb) -> {
            if (warehouseIds == null) {
                return null;
            }
            if (warehouseIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.join("inboundReceipt").get("warehouseId").in(warehouseIds);
        };
    }

    public static Specification<PutawayTask> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            try {
                PutawayStatus enumStatus = PutawayStatus.valueOf(status.trim().toUpperCase());
                return cb.equal(root.get("status"), enumStatus);
            } catch (IllegalArgumentException e) {
                return cb.disjunction();
            }
        };
    }
}
