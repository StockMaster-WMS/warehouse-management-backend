package com.product_service.repository;

import com.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Optional<Product> findBySku(String sku);

	Optional<Product> findByBarcodeEan13(String barcodeEan13);

	boolean existsBySku(String sku);

	boolean existsByBarcodeEan13(String barcodeEan13);
}