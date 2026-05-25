package com.warehouse_service.repository;

import com.warehouse_service.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID>, JpaSpecificationExecutor<Warehouse> {

	Optional<Warehouse> findByCode(String code);

	@Override
	@EntityGraph(attributePaths = "managers")
	Optional<Warehouse> findById(UUID id);

	@Override
	@EntityGraph(attributePaths = "managers")
	Page<Warehouse> findAll(Specification<Warehouse> spec, Pageable pageable);

	boolean existsByCode(String code);

	long countByIsActiveTrue();

	long countByIsActiveFalse();

	@Query("select distinct w.id from Warehouse w join w.managers m where m.id = :managerId")
	List<UUID> findIdsByManagerId(@Param("managerId") UUID managerId);

	@EntityGraph(attributePaths = "managers")
	@Query("select distinct w from Warehouse w join w.managers m where m.id = :managerId")
	List<Warehouse> findByManagerId(@Param("managerId") UUID managerId);

	@EntityGraph(attributePaths = "managers")
	List<Warehouse> findByIdIn(Collection<UUID> ids);

	@Query("select count(distinct w) from Warehouse w join w.managers m where m.id = :managerId")
	long countByManagerId(@Param("managerId") UUID managerId);

	@Query("select count(distinct w) from Warehouse w join w.managers m where m.id = :managerId and w.isActive = true")
	long countActiveByManagerId(@Param("managerId") UUID managerId);

	@Query("select count(distinct w) from Warehouse w join w.managers m where m.id = :managerId and w.isActive = false")
	long countInactiveByManagerId(@Param("managerId") UUID managerId);

	long countByIdIn(Collection<UUID> ids);

	long countByIdInAndIsActiveTrue(Collection<UUID> ids);

	long countByIdInAndIsActiveFalse(Collection<UUID> ids);
}
