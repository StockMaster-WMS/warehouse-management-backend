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

	@Query("""
			SELECT COUNT(o) FROM SalesOrder o
			WHERE o.status NOT IN :statuses
			  AND o.warehouseId IN :warehouseIds
			""")
	long countByStatusNotInAndWarehouseIdIn(
			@Param("statuses") Collection<SalesOrderStatus> statuses,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("""
			SELECT COUNT(o) FROM SalesOrder o
			WHERE o.status = :status
			  AND o.warehouseId IN :warehouseIds
			""")
	long countByStatusInWarehouses(
			@Param("status") SalesOrderStatus status,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("""
			SELECT COUNT(DISTINCT o) FROM SalesOrder o
			WHERE o.status = :status
			  AND NOT EXISTS (
			      SELECT p.id FROM PickingItem p
			      WHERE p.soItem.salesOrder = o
			  )
			""")
	long countWithoutPickingByStatus(@Param("status") SalesOrderStatus status);

	@Query("""
			SELECT COUNT(DISTINCT o) FROM SalesOrder o
			WHERE o.status = :status
			  AND o.warehouseId IN :warehouseIds
			  AND NOT EXISTS (
			      SELECT p.id FROM PickingItem p
			      WHERE p.soItem.salesOrder = o
			  )
			""")
	long countWithoutPickingByStatusInWarehouses(
			@Param("status") SalesOrderStatus status,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			java.time.OffsetDateTime fromDate,
			java.time.OffsetDateTime toDate);

	long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			SalesOrderStatus status,
			java.time.OffsetDateTime fromDate,
			java.time.OffsetDateTime toDate);

	@Query("""
			SELECT COUNT(o) FROM SalesOrder o
			WHERE o.status NOT IN :statuses
			  AND o.createdAt >= :fromDate
			  AND o.createdAt < :toDate
			""")
	long countByStatusNotInBetween(
			@Param("statuses") Collection<SalesOrderStatus> statuses,
			@Param("fromDate") java.time.OffsetDateTime fromDate,
			@Param("toDate") java.time.OffsetDateTime toDate);

	@Query("""
			SELECT COUNT(o) FROM SalesOrder o
			WHERE o.createdAt >= :fromDate
			  AND o.createdAt < :toDate
			  AND o.warehouseId IN :warehouseIds
			""")
	long countCreatedBetweenInWarehouses(
			@Param("fromDate") java.time.OffsetDateTime fromDate,
			@Param("toDate") java.time.OffsetDateTime toDate,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("""
			SELECT COUNT(o) FROM SalesOrder o
			WHERE o.status = :status
			  AND o.createdAt >= :fromDate
			  AND o.createdAt < :toDate
			  AND o.warehouseId IN :warehouseIds
			""")
	long countByStatusBetweenInWarehouses(
			@Param("status") SalesOrderStatus status,
			@Param("fromDate") java.time.OffsetDateTime fromDate,
			@Param("toDate") java.time.OffsetDateTime toDate,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("""
			SELECT COUNT(o) FROM SalesOrder o
			WHERE o.status NOT IN :statuses
			  AND o.createdAt >= :fromDate
			  AND o.createdAt < :toDate
			  AND o.warehouseId IN :warehouseIds
			""")
	long countByStatusNotInBetweenInWarehouses(
			@Param("statuses") Collection<SalesOrderStatus> statuses,
			@Param("fromDate") java.time.OffsetDateTime fromDate,
			@Param("toDate") java.time.OffsetDateTime toDate,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("SELECT COALESCE(SUM(i.unitPrice * i.shippedQty), 0) " +
			"FROM SalesOrderItem i JOIN i.salesOrder o " +
			"WHERE o.status = 'SHIPPED' AND o.createdAt >= :fromDate")
	java.math.BigDecimal sumTotalRevenue(@Param("fromDate") java.time.OffsetDateTime fromDate);

	@Query("SELECT COALESCE(SUM(i.unitPrice * i.shippedQty), 0) " +
			"FROM SalesOrderItem i JOIN i.salesOrder o " +
			"WHERE o.status = 'SHIPPED' AND o.createdAt >= :fromDate AND o.createdAt < :toDate")
	java.math.BigDecimal sumTotalRevenueBetween(
			@Param("fromDate") java.time.OffsetDateTime fromDate,
			@Param("toDate") java.time.OffsetDateTime toDate);

	@Query("SELECT COALESCE(SUM(i.unitPrice * i.shippedQty), 0) " +
			"FROM SalesOrderItem i JOIN i.salesOrder o " +
			"WHERE o.status = 'SHIPPED' AND o.createdAt >= :fromDate AND o.createdAt < :toDate " +
			"AND o.warehouseId IN :warehouseIds")
	java.math.BigDecimal sumTotalRevenueBetweenInWarehouses(
			@Param("fromDate") java.time.OffsetDateTime fromDate,
			@Param("toDate") java.time.OffsetDateTime toDate,
			@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("""
			SELECT COUNT(DISTINCT o.customerId) FROM SalesOrder o
			WHERE o.customerId IS NOT NULL
			  AND o.warehouseId IN :warehouseIds
			""")
	long countDistinctCustomersInWarehouses(@Param("warehouseIds") Collection<UUID> warehouseIds);
}
