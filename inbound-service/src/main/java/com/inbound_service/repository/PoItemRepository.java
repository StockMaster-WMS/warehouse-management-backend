package com.inbound_service.repository;

import com.inbound_service.entity.PoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PoItemRepository extends JpaRepository<PoItem, UUID> {
}