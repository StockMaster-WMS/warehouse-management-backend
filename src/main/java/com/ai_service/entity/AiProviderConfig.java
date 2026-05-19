package com.ai_service.entity;

import com.common.util.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "ai_provider_configs")
@Getter
@Setter
public class AiProviderConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "api_key_encrypted", columnDefinition = "text")
    private String apiKeyEncrypted;

    @Column(name = "key_preview", length = 80)
    private String keyPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidUtils.uuidV7();
        }
        normalizeProvider();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        normalizeProvider();
        updatedAt = OffsetDateTime.now();
    }

    private void normalizeProvider() {
        if (provider != null) {
            provider = provider.trim().toLowerCase(Locale.ROOT);
        }
    }
}
