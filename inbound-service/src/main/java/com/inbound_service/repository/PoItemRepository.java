package com.inbound_service.repository;

import com.inbound_service.entity.PoItem;
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

public interface PoItemRepository extends JpaRepository<PoItem, UUID>, JpaSpecificationExecutor<PoItem> {

	@Override
	@EntityGraph(attributePaths = {"purchaseOrder"})
	Page<PoItem> findAll(Specification<PoItem> spec, Pageable pageable);

	List<PoItem> findByPurchaseOrderId(UUID purchaseOrderId);

	Optional<PoItem> findByPurchaseOrderIdAndLineNumber(UUID purchaseOrderId, Short lineNumber);

	@Query("SELECT p FROM PoItem p JOIN FETCH p.purchaseOrder WHERE p.id = :id")
	Optional<PoItem> findByIdWithPurchaseOrder(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PoItem p JOIN FETCH p.purchaseOrder WHERE p.id = :id")
	Optional<PoItem> findByIdWithPurchaseOrderForUpdate(@Param("id") UUID id);
}
