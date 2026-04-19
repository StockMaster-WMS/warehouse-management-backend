package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {

	Optional<SalesOrder> findBySoNumber(String soNumber);

	boolean existsBySoNumber(String soNumber);

	long countByStatusNotIn(Collection<SalesOrderStatus> statuses);
}
