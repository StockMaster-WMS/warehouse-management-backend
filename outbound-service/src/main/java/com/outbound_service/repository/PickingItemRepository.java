package com.outbound_service.repository;

import com.outbound_service.entity.PickingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PickingItemRepository extends JpaRepository<PickingItem, UUID> {

	List<PickingItem> findBySoItem_Id(UUID soItemId);

	@Query("SELECT p FROM PickingItem p JOIN FETCH p.soItem s WHERE s.salesOrder.id = :salesOrderId")
	List<PickingItem> findBySalesOrderIdWithSoItem(@Param("salesOrderId") UUID salesOrderId);

	List<PickingItem> findByProductId(UUID productId);

	List<PickingItem> findByLocationId(UUID locationId);
}