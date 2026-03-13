package com.warehouse_service.repository;

import com.warehouse_service.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

	Optional<Warehouse> findByCode(String code);

	boolean existsByCode(String code);
}