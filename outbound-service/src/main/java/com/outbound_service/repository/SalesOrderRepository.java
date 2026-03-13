package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

	Optional<SalesOrder> findBySoNumber(String soNumber);

	boolean existsBySoNumber(String soNumber);
}