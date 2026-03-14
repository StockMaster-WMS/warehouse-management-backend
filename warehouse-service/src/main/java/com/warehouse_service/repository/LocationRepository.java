package com.warehouse_service.repository;

import com.warehouse_service.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

	List<Location> findByWarehouseId(UUID warehouseId);

	Optional<Location> findByWarehouseIdAndCode(UUID warehouseId, String code);
}