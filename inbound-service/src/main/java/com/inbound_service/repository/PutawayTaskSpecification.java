package com.inbound_service.repository;

import com.inbound_service.entity.PutawayTask;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class PutawayTaskSpecification {

    public static Specification<PutawayTask> hasPoItemId(UUID poItemId) {
        return (root, query, cb) -> poItemId == null ? null : cb.equal(root.join("poItem").get("id"), poItemId);
    }

    public static Specification<PutawayTask> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) {
                return null;
            }
            return cb.equal(cb.lower(root.get("status")), status.toLowerCase());
        };
    }
}
