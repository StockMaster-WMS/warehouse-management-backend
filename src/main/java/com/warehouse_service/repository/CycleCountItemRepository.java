package com.warehouse_service.repository;

import com.warehouse_service.entity.CycleCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CycleCountItemRepository extends JpaRepository<CycleCountItem, UUID> {
}
