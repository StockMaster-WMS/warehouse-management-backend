package com.inbound_service.repository;

import com.inbound_service.entity.PoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoItemRepository extends JpaRepository<PoItem, UUID> {

	List<PoItem> findByPurchaseOrderId(UUID purchaseOrderId);

	Optional<PoItem> findByPurchaseOrderIdAndLineNumber(UUID purchaseOrderId, Short lineNumber);
}