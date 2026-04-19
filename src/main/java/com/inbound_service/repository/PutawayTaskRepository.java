package com.inbound_service.repository;

import com.inbound_service.entity.PutawayTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PutawayTaskRepository extends JpaRepository<PutawayTask, UUID>, JpaSpecificationExecutor<PutawayTask> {

    @Override
    @EntityGraph(attributePaths = {"poItem"})
    Page<PutawayTask> findAll(Specification<PutawayTask> spec, Pageable pageable);

    @Query("SELECT t FROM PutawayTask t JOIN FETCH t.poItem p WHERE p.purchaseOrder.id = :purchaseOrderId")
    List<PutawayTask> findByPurchaseOrderIdWithPoItem(@Param("purchaseOrderId") UUID purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PutawayTask t LEFT JOIN FETCH t.poItem p LEFT JOIN FETCH p.purchaseOrder LEFT JOIN FETCH t.inboundReceipt WHERE t.id = :id")
    Optional<PutawayTask> findByIdWithPoAndOrderForUpdate(@Param("id") UUID id);

    List<PutawayTask> findByInboundReceiptId(UUID inboundReceiptId);
}
