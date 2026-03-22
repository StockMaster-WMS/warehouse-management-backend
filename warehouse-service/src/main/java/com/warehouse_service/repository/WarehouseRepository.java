package com.warehouse_service.repository;

import com.warehouse_service.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID>, JpaSpecificationExecutor<Warehouse> {

	Optional<Warehouse> findByCode(String code);

	boolean existsByCode(String code);

	long countByIsActiveTrue();

	long countByIsActiveFalse();
}