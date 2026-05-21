package com.warehouse_service.repository;

import com.warehouse_service.entity.CycleCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CycleCountRepository extends JpaRepository<CycleCount, UUID>, JpaSpecificationExecutor<CycleCount> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<CycleCount> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct c from CycleCount c left join fetch c.items where c.id = :id")
    Optional<CycleCount> findByIdWithItemsForUpdate(@Param("id") UUID id);
}
