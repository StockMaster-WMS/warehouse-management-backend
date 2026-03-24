package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID>, JpaSpecificationExecutor<StockLevel> {

	@Override
	@EntityGraph(attributePaths = {"warehouse", "location"})
	Page<StockLevel> findAll(Specification<StockLevel> spec, Pageable pageable);

	List<StockLevel> findByWarehouseId(UUID warehouseId);

	List<StockLevel> findByLocationId(UUID locationId);

	List<StockLevel> findByProductId(UUID productId);

	List<StockLevel> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

	Optional<StockLevel> findByLocationIdAndProductIdAndLotNumber(UUID locationId, UUID productId, String lotNumber);

	@Query("select count(distinct s.warehouse.id) from StockLevel s where s.qtyOnHand > 0")
	long countWarehousesWithStock();
}
