package com.warehouse_service.repository;

import com.warehouse_service.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

	List<Location> findByWarehouseId(UUID warehouseId);

	Optional<Location> findByWarehouseIdAndCode(UUID warehouseId, String code);

	@Query("""
			select l.warehouse.id as warehouseId,
			       count(distinct l.zone) as zonesCount,
			       count(l.id) as binsCount
			from Location l
			where l.warehouse.id in :warehouseIds
		group by l.warehouse.id
			""")
	List<WarehouseLocationStatsView> getLocationStatsByWarehouseIds(@Param("warehouseIds") List<UUID> warehouseIds);

	interface WarehouseLocationStatsView {
		UUID getWarehouseId();

		Long getZonesCount();

		Long getBinsCount();
	}
}