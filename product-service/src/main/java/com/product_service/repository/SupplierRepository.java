package com.product_service.repository;

import com.product_service.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

	Optional<Supplier> findByCode(String code);

	Optional<Supplier> findByTaxCode(String taxCode);

	boolean existsByCode(String code);

	boolean existsByTaxCode(String taxCode);
}