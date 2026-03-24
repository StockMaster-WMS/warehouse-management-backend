package com.warehouse_service.repository;

import com.warehouse_service.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID>, JpaSpecificationExecutor<Location> {

	List<Location> findByWarehouseId(UUID warehouseId);

	Optional<Location> findByWarehouseIdAndCode(UUID warehouseId, String code);
}
