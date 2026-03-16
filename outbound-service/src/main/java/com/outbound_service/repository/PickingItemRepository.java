package com.outbound_service.repository;

import com.outbound_service.entity.PickingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickingItemRepository extends JpaRepository<PickingItem, UUID> {

	List<PickingItem> findBySoItemId(UUID soItemId);

	List<PickingItem> findByProductId(UUID productId);

	List<PickingItem> findByLocationId(UUID locationId);
}