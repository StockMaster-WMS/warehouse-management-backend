package com.inbound_service.repository;

import com.inbound_service.entity.Rma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RmaRepository extends JpaRepository<Rma, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct r from Rma r left join fetch r.items where r.id = :id")
    Optional<Rma> findByIdWithItemsForUpdate(@Param("id") UUID id);

    @Query("select distinct r from Rma r left join fetch r.items where r.salesOrderId = :salesOrderId")
    List<Rma> findBySalesOrderIdWithItems(@Param("salesOrderId") UUID salesOrderId);
}
