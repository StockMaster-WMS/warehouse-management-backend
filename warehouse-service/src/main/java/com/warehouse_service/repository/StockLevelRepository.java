package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

	List<StockLevel> findByWarehouseId(UUID warehouseId);

	List<StockLevel> findByLocationId(UUID locationId);

	List<StockLevel> findByProductId(UUID productId);

	List<StockLevel> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

	Optional<StockLevel> findByLocationIdAndProductIdAndLotNumber(UUID locationId, UUID productId, String lotNumber);
}