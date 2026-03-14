package com.inbound_service.repository;

import com.inbound_service.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

	Optional<PurchaseOrder> findByPoNumber(String poNumber);

	boolean existsByPoNumber(String poNumber);
}