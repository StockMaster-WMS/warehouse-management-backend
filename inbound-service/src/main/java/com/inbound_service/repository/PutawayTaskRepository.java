package com.inbound_service.repository;

import com.inbound_service.entity.PutawayTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PutawayTaskRepository extends JpaRepository<PutawayTask, UUID> {

    List<PutawayTask> findByPoItemId(UUID poItemId);

    List<PutawayTask> findByStatus(String status);

    @Query("SELECT t FROM PutawayTask t LEFT JOIN FETCH t.poItem p LEFT JOIN FETCH p.purchaseOrder WHERE t.id = :id")
    Optional<PutawayTask> findByIdWithPoAndOrder(@Param("id") UUID id);
}
