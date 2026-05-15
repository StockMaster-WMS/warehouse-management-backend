package com.inbound_service.repository;

import com.inbound_service.entity.Rma;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RmaRepository extends JpaRepository<Rma, UUID> {
}
