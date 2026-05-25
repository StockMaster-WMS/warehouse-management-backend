package com.warehouse_service.repository;

import com.warehouse_service.entity.CycleCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CycleCountRepository extends JpaRepository<CycleCount, UUID>, JpaSpecificationExecutor<CycleCount> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<CycleCount> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct c from CycleCount c left join fetch c.items where c.id = :id")
    Optional<CycleCount> findByIdWithItemsForUpdate(@Param("id") UUID id);

    @Query("""
            select count(i) from CycleCount c
            join c.items i
            where c.status = 'APPROVED'
              and i.countedQty is not null
            """)
    long countApprovedCountedItems();

    @Query("""
            select count(i) from CycleCount c
            join c.items i
            where c.status = 'APPROVED'
              and i.countedQty is not null
              and c.warehouseId in :warehouseIds
            """)
    long countApprovedCountedItemsInWarehouses(@Param("warehouseIds") Collection<UUID> warehouseIds);

    @Query("""
            select count(i) from CycleCount c
            join c.items i
            where c.status = 'APPROVED'
              and i.countedQty is not null
              and coalesce(i.discrepancy, 0) = 0
            """)
    long countAccurateApprovedCountedItems();

    @Query("""
            select count(i) from CycleCount c
            join c.items i
            where c.status = 'APPROVED'
              and i.countedQty is not null
              and coalesce(i.discrepancy, 0) = 0
              and c.warehouseId in :warehouseIds
            """)
    long countAccurateApprovedCountedItemsInWarehouses(@Param("warehouseIds") Collection<UUID> warehouseIds);

    @Query("""
            select count(i) from CycleCount c
            join c.items i
            where c.status in ('PENDING_REVIEW', 'APPROVED')
              and abs(coalesce(i.discrepancy, 0)) >= :threshold
            """)
    long countLargeVarianceItems(@Param("threshold") int threshold);

    @Query("""
            select count(i) from CycleCount c
            join c.items i
            where c.status in ('PENDING_REVIEW', 'APPROVED')
              and abs(coalesce(i.discrepancy, 0)) >= :threshold
              and c.warehouseId in :warehouseIds
            """)
    long countLargeVarianceItemsInWarehouses(
            @Param("threshold") int threshold,
            @Param("warehouseIds") Collection<UUID> warehouseIds);
}
