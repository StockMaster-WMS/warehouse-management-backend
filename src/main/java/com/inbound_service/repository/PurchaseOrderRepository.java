package com.inbound_service.repository;

import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

	Optional<PurchaseOrder> findByPoNumber(String poNumber);

	boolean existsByPoNumber(String poNumber);

	boolean existsBySupplierId(UUID supplierId);

	long countByStatusNotIn(Collection<PurchaseOrderStatus> statuses);
}
