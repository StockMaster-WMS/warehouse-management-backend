package com.ai_service.repository;

import com.ai_service.entity.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, UUID> {

    Optional<AiProviderConfig> findByProvider(String provider);
}
