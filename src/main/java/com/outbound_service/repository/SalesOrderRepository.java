package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {

	Optional<SalesOrder> findBySoNumber(String soNumber);

	boolean existsBySoNumber(String soNumber);

	long countByStatus(SalesOrderStatus status);

	long countByStatusNotIn(Collection<SalesOrderStatus> statuses);

	@Query("SELECT COALESCE(SUM(i.unitPrice * i.shippedQty), 0) " +
			"FROM SalesOrderItem i JOIN i.salesOrder o " +
			"WHERE o.status = 'SHIPPED' AND o.createdAt >= :fromDate")
	java.math.BigDecimal sumTotalRevenue(@Param("fromDate") java.time.OffsetDateTime fromDate);
}
