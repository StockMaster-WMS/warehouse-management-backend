package com.product_service.repository;

import com.product_service.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {

	@Override
	@EntityGraph(attributePaths = {"parent"})
	Page<Category> findAll(Specification<Category> spec, Pageable pageable);

	Optional<Category> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByParentId(UUID parentId);

	List<Category> findByParentId(UUID parentId);
}
