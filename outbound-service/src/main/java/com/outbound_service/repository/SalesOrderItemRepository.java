package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrderItem;
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

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, UUID>, JpaSpecificationExecutor<SalesOrderItem> {

    @Override
    @EntityGraph(attributePaths = {"salesOrder"})
    Page<SalesOrderItem> findAll(Specification<SalesOrderItem> spec, Pageable pageable);

    List<SalesOrderItem> findBySalesOrder_Id(UUID salesOrderId);

    Optional<SalesOrderItem> findBySalesOrder_IdAndLineNumber(UUID salesOrderId, Short lineNumber);

    @Query("SELECT s FROM SalesOrderItem s JOIN FETCH s.salesOrder WHERE s.id = :id")
    Optional<SalesOrderItem> findByIdWithSalesOrder(@Param("id") UUID id);
}
