package com.ai_service.service.provider;

import com.ai_service.dto.AiProviderKeyStatusResponse;
import com.ai_service.dto.UpdateAiProviderKeyRequest;
import com.ai_service.entity.AiProviderConfig;
import com.ai_service.repository.AiProviderConfigRepository;
import com.common.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiProviderConfigService {

    public static final String GEMINI_PROVIDER = "gemini";
    public static final String OPENAI_PROVIDER = "openai";

    private static final Map<String, ProviderDefinition> SUPPORTED_PROVIDERS = new LinkedHashMap<>();

    static {
        SUPPORTED_PROVIDERS.put(GEMINI_PROVIDER, new ProviderDefinition(GEMINI_PROVIDER, "Trợ lý AI Google"));
        SUPPORTED_PROVIDERS.put(OPENAI_PROVIDER, new ProviderDefinition(OPENAI_PROVIDER, "Trợ lý AI OpenAI"));
    }

    private final AiProviderConfigRepository repository;
    private final AiSecretCryptoService cryptoService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public AiProviderKeyStatusResponse getGeminiKeyStatus() {
        return getKeyStatus(GEMINI_PROVIDER);
    }

    @Transactional
    public AiProviderKeyStatusResponse updateGeminiKey(UpdateAiProviderKeyRequest request) {
        return updateKey(GEMINI_PROVIDER, request);
    }

    @Transactional
    public AiProviderKeyStatusResponse clearGeminiKey() {
        return clearKey(GEMINI_PROVIDER);
    }

    @Transactional(readOnly = true)
    public List<AiProviderKeyStatusResponse> getProviderKeyStatuses() {
        return SUPPORTED_PROVIDERS.keySet().stream()
                .map(this::getKeyStatus)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiProviderKeyStatusResponse getKeyStatus(String provider) {
        ProviderDefinition definition = providerDefinition(provider);
        return toStatus(definition, repository.findByProvider(definition.provider()).orElse(null));
    }

    @Transactional
    public AiProviderKeyStatusResponse updateKey(String provider, UpdateAiProviderKeyRequest request) {
        ProviderDefinition definition = providerDefinition(provider);
        String apiKey = request == null ? null : request.apiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("API key không được để trống");
        }
        AiProviderConfig config = repository.findByProvider(definition.provider())
                .orElseGet(() -> {
                    AiProviderConfig created = new AiProviderConfig();
                    created.setProvider(definition.provider());
                    return created;
                });
        String trimmedKey = apiKey.trim();
        config.setApiKeyEncrypted(cryptoService.encrypt(trimmedKey));
        config.setKeyPreview(maskKey(trimmedKey));
        AiProviderConfig saved = repository.save(config);
        auditLogService.record("AI", "UPDATE", "Cập nhật API key " + definition.label(),
                "AI_PROVIDER_CONFIG", saved.getId(), definition.provider(),
                null, Map.of("provider", definition.provider(), "configured", true),
                "Cập nhật cấu hình AI từ giao diện", Map.of("provider", definition.provider()));
        return toStatus(definition, saved);
    }

    @Transactional
    public AiProviderKeyStatusResponse clearKey(String provider) {
        ProviderDefinition definition = providerDefinition(provider);
        Optional<AiProviderConfig> existing = repository.findByProvider(definition.provider());
        if (existing.isEmpty()) {
            return toStatus(definition, null);
        }
        AiProviderConfig config = existing.get();
        config.setApiKeyEncrypted(null);
        config.setKeyPreview(null);
        AiProviderConfig saved = repository.save(config);
        auditLogService.record("AI", "UPDATE", "Xóa API key " + definition.label(),
                "AI_PROVIDER_CONFIG", saved.getId(), definition.provider(),
                null, Map.of("provider", definition.provider(), "configured", false),
                "Xóa cấu hình AI từ giao diện", Map.of("provider", definition.provider()));
        return toStatus(definition, saved);
    }

    @Transactional(readOnly = true)
    public Optional<String> findApiKey(String provider) {
        String normalized = normalizeProvider(provider);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        return repository.findByProvider(normalized)
                .map(AiProviderConfig::getApiKeyEncrypted)
                .filter(StringUtils::hasText)
                .map(cryptoService::decrypt)
                .filter(StringUtils::hasText);
    }

    private AiProviderKeyStatusResponse toStatus(ProviderDefinition definition, AiProviderConfig config) {
        if (config == null || !StringUtils.hasText(config.getApiKeyEncrypted())) {
            return new AiProviderKeyStatusResponse(definition.provider(), definition.label(), false, null,
                    config == null ? null : config.getUpdatedAt());
        }
        return new AiProviderKeyStatusResponse(config.getProvider(), definition.label(), true,
                config.getKeyPreview(), config.getUpdatedAt());
    }

    private ProviderDefinition providerDefinition(String provider) {
        String normalized = normalizeProvider(provider);
        ProviderDefinition definition = SUPPORTED_PROVIDERS.get(normalized);
        if (definition == null) {
            throw new IllegalArgumentException("Provider AI chưa được hỗ trợ: " + provider);
        }
        return definition;
    }

    private String normalizeProvider(String provider) {
        return StringUtils.hasText(provider)
                ? provider.trim().toLowerCase(Locale.ROOT)
                : null;
    }

    private String maskKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        return trimmed.substring(0, Math.min(4, trimmed.length()))
                + "********"
                + trimmed.substring(Math.max(trimmed.length() - 4, 4));
    }

    private record ProviderDefinition(String provider, String label) {
    }
}
