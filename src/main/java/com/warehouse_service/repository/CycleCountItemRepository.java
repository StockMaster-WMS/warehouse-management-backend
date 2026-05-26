package com.warehouse_service.repository;

import com.warehouse_service.entity.CycleCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CycleCountItemRepository extends JpaRepository<CycleCountItem, UUID> {
    interface CycleCountItemStatsView {
        UUID getCycleCountId();

        Long getTotalLines();

        Long getCountedLines();

        Long getDiscrepancyLines();

        Long getTotalAbsDiscrepancy();
    }

    @Query("""
            select i.cycleCount.id as cycleCountId,
                   count(i) as totalLines,
                   sum(case when i.countedQty is not null then 1 else 0 end) as countedLines,
                   sum(case when coalesce(i.discrepancy, 0) <> 0 then 1 else 0 end) as discrepancyLines,
                   coalesce(sum(abs(coalesce(i.discrepancy, 0))), 0) as totalAbsDiscrepancy
            from CycleCountItem i
            where i.cycleCount.id in :cycleCountIds
            group by i.cycleCount.id
            """)
    List<CycleCountItemStatsView> summarizeByCycleCountIds(@Param("cycleCountIds") Collection<UUID> cycleCountIds);
}
