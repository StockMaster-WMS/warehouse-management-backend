package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {
}