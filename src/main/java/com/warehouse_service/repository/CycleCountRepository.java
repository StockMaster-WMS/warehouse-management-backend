package com.warehouse_service.repository;

import com.warehouse_service.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
}
