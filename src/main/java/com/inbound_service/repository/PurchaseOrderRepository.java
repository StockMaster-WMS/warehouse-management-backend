package com.inbound_service.repository;

import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

	Optional<PurchaseOrder> findByPoNumber(String poNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PurchaseOrder p where p.id = :id")
	Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);

	boolean existsByPoNumber(String poNumber);

	boolean existsBySupplierId(UUID supplierId);

	long countByStatusNotIn(Collection<PurchaseOrderStatus> statuses);

	@Query("""
			select count(p) from PurchaseOrder p
			where p.status not in :statuses
			  and p.warehouseId in :warehouseIds
			""")
	long countByStatusNotInAndWarehouseIdIn(
			@Param("statuses") Collection<PurchaseOrderStatus> statuses,
			@Param("warehouseIds") Collection<UUID> warehouseIds);
}
