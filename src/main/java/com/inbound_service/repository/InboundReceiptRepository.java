package com.inbound_service.repository;

import com.inbound_service.entity.InboundReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID>,
        JpaSpecificationExecutor<InboundReceipt> {

    @Override
    @EntityGraph(attributePaths = {"purchaseOrder", "items", "items.poItem"})
    Page<InboundReceipt> findAll(Specification<InboundReceipt> spec, Pageable pageable);

    List<InboundReceipt> findByPurchaseOrderId(UUID purchaseOrderId);

    Optional<InboundReceipt> findByReceiptNumber(String receiptNumber);

    Optional<InboundReceipt> findByIdempotencyKey(String idempotencyKey);

    boolean existsByReceiptNumber(String receiptNumber);
}
