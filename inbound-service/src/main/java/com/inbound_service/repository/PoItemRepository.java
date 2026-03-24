package com.inbound_service.repository;

import com.inbound_service.entity.PoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoItemRepository extends JpaRepository<PoItem, UUID> {

	List<PoItem> findByPurchaseOrderId(UUID purchaseOrderId);

	Optional<PoItem> findByPurchaseOrderIdAndLineNumber(UUID purchaseOrderId, Short lineNumber);

	@Query("SELECT p FROM PoItem p JOIN FETCH p.purchaseOrder WHERE p.id = :id")
	Optional<PoItem> findByIdWithPurchaseOrder(@Param("id") UUID id);
}