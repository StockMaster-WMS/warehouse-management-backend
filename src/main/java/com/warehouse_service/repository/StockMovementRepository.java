package com.warehouse_service.repository;

import com.warehouse_service.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {

    interface DailyMovementView {
        LocalDate getMovementDate();

        Long getInboundQty();

        Long getOutboundQty();
    }

    @Override
    @EntityGraph(attributePaths = {"warehouse", "location"})
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            select cast(created_at as date) as "movementDate",
                   coalesce(sum(case when qty_change > 0 then qty_change else 0 end), 0) as "inboundQty",
                   coalesce(sum(case when qty_change < 0 then abs(qty_change) else 0 end), 0) as "outboundQty"
            from stock_movements
            where created_at >= :fromDate
              and created_at < :toDate
            group by cast(created_at as date)
            order by cast(created_at as date)
            """, nativeQuery = true)
    List<DailyMovementView> sumDailyMovements(
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate);

    @Query(value = """
            select cast(created_at as date) as "movementDate",
                   coalesce(sum(case when qty_change > 0 then qty_change else 0 end), 0) as "inboundQty",
                   coalesce(sum(case when qty_change < 0 then abs(qty_change) else 0 end), 0) as "outboundQty"
            from stock_movements
            where created_at >= :fromDate
              and created_at < :toDate
              and warehouse_id in (:warehouseIds)
            group by cast(created_at as date)
            order by cast(created_at as date)
            """, nativeQuery = true)
    List<DailyMovementView> sumDailyMovementsInWarehouses(
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            @Param("warehouseIds") Collection<UUID> warehouseIds);
}
