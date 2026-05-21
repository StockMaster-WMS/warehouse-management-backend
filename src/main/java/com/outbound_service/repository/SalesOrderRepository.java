package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {

	Optional<SalesOrder> findBySoNumber(String soNumber);

	@EntityGraph(attributePaths = {"items"})
	Optional<SalesOrder> findWithItemsById(UUID id);

	@EntityGraph(attributePaths = {"items"})
	@Query("""
			SELECT DISTINCT o FROM SalesOrder o
			LEFT JOIN o.items i
			WHERE o.status = 'SHIPPED'
			  AND (
			      o.customerId = :customerId
			      OR (:customerName IS NOT NULL AND LOWER(o.customerName) = LOWER(:customerName))
			  )
			ORDER BY o.createdAt DESC
			""")
	List<SalesOrder> findReturnableByCustomer(
			@Param("customerId") UUID customerId,
			@Param("customerName") String customerName);

	@EntityGraph(attributePaths = {"items"})
	@Query("""
			SELECT DISTINCT o FROM SalesOrder o
			LEFT JOIN o.items i
			WHERE o.status = 'SHIPPED'
			  AND o.warehouseId IN :warehouseIds
			  AND (
			      o.customerId = :customerId
			      OR (:customerName IS NOT NULL AND LOWER(o.customerName) = LOWER(:customerName))
			  )
			ORDER BY o.createdAt DESC
			""")
	List<SalesOrder> findReturnableByCustomerInWarehouses(
			@Param("customerId") UUID customerId,
			@Param("customerName") String customerName,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	boolean existsBySoNumber(String soNumber);

	long countByStatus(SalesOrderStatus status);

	long countByStatusNotIn(Collection<SalesOrderStatus> statuses);

	@Query("SELECT COALESCE(SUM(i.unitPrice * i.shippedQty), 0) " +
			"FROM SalesOrderItem i JOIN i.salesOrder o " +
			"WHERE o.status = 'SHIPPED' AND o.createdAt >= :fromDate")
	java.math.BigDecimal sumTotalRevenue(@Param("fromDate") java.time.OffsetDateTime fromDate);
}
