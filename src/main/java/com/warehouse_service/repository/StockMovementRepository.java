package com.warehouse_service.repository;

import com.warehouse_service.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {

    @Override
    @EntityGraph(attributePaths = {"warehouse", "location"})
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);
}
