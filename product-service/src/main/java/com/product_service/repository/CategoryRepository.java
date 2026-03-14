package com.product_service.repository;

import com.product_service.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	Optional<Category> findByCode(String code);

	boolean existsByCode(String code);
}