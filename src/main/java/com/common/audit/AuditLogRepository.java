package com.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    List<AuditLog> findTop10ByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    @Query("""
            select count(a) from AuditLog a
            where a.module = :module
              and a.actionType = :actionType
              and a.createdAt >= :fromDate
              and a.createdAt < :toDate
            """)
    long countByModuleActionTypeBetween(
            @Param("module") String module,
            @Param("actionType") String actionType,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate);
}
