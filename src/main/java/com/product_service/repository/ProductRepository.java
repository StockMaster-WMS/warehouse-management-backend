package com.product_service.repository;

import com.product_service.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = {"category", "primarySupplier"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcodeEan13(String barcodeEan13);

    boolean existsBySku(String sku);

    boolean existsByBarcodeEan13(String barcodeEan13);

    Optional<Product> findByNameIgnoreCase(String name);
}
