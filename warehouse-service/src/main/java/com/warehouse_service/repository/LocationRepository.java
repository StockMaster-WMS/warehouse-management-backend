package com.warehouse_service.repository;

import com.warehouse_service.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID>, JpaSpecificationExecutor<Location> {

	@Override
	@EntityGraph(attributePaths = {"warehouse"})
	Page<Location> findAll(Specification<Location> spec, Pageable pageable);

	List<Location> findByWarehouseId(UUID warehouseId);

	boolean existsByWarehouseId(UUID warehouseId);

	Optional<Location> findByWarehouseIdAndCode(UUID warehouseId, String code);
}
