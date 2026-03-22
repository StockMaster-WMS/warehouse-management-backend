package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

	List<StockLevel> findByWarehouseId(UUID warehouseId);

	List<StockLevel> findByLocationId(UUID locationId);

	List<StockLevel> findByProductId(UUID productId);

	List<StockLevel> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

	Optional<StockLevel> findByLocationIdAndProductIdAndLotNumber(UUID locationId, UUID productId, String lotNumber);

	@Query("""
			select s.warehouse.id as warehouseId,
			       count(distinct s.location.id) as stockedBins
			from StockLevel s
			where s.warehouse.id in :warehouseIds and s.qtyOnHand > 0
		group by s.warehouse.id
			""")
	List<WarehouseStockedBinsView> getStockedBinsByWarehouseIds(@Param("warehouseIds") List<UUID> warehouseIds);

	@Query("select count(distinct s.warehouse.id) from StockLevel s where s.qtyOnHand > 0")
	long countWarehousesWithStock();

	interface WarehouseStockedBinsView {
		UUID getWarehouseId();

		Long getStockedBins();
	}
}