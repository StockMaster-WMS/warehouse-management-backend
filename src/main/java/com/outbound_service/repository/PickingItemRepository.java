package com.outbound_service.repository;

import com.outbound_service.entity.PickingItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Collection;

public interface PickingItemRepository extends JpaRepository<PickingItem, UUID>, JpaSpecificationExecutor<PickingItem> {

	@Override
	@EntityGraph(attributePaths = {"soItem", "soItem.salesOrder"})
	Page<PickingItem> findAll(Specification<PickingItem> spec, Pageable pageable);

	List<PickingItem> findBySoItem_Id(UUID soItemId);

	@Query("SELECT p FROM PickingItem p JOIN FETCH p.soItem s WHERE s.salesOrder.id = :salesOrderId")
	List<PickingItem> findBySalesOrderIdWithSoItem(@Param("salesOrderId") UUID salesOrderId);

	List<PickingItem> findByProductId(UUID productId);

	List<PickingItem> findByLocationId(UUID locationId);

	boolean existsBySoItem_SalesOrder_Id(UUID salesOrderId);

	boolean existsBySoItem_Id(UUID soItemId);

	@Query("SELECT p FROM PickingItem p JOIN FETCH p.soItem s JOIN FETCH s.salesOrder WHERE p.id = :id")
	Optional<PickingItem> findByIdWithSoAndOrder(@Param("id") UUID id);

	@Query("""
			SELECT COUNT(p) FROM PickingItem p
			JOIN p.soItem s
			JOIN s.salesOrder o
			WHERE p.status <> 'PICKED'
			  AND o.status = 'PICKING'
			  AND o.createdAt < :cutoff
			""")
	long countOverduePickingTasks(@Param("cutoff") OffsetDateTime cutoff);

	@Query("""
			SELECT COUNT(p) FROM PickingItem p
			JOIN p.soItem s
			JOIN s.salesOrder o
			WHERE p.status <> 'PICKED'
			  AND o.status = 'PICKING'
			  AND o.createdAt < :cutoff
			  AND o.warehouseId IN :warehouseIds
			""")
	long countOverduePickingTasksInWarehouses(
			@Param("cutoff") OffsetDateTime cutoff,
			@Param("warehouseIds") Collection<UUID> warehouseIds);
}
