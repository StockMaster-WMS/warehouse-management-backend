package com.common.audit;

import com.common.api.PagedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Transactional
    public void record(String module, String actionType, String action, String entityType,
            UUID entityId, String entityName, Object beforeSnapshot, Object afterSnapshot,
            String reason, Map<String, ?> metadata) {
        try {
            Actor actor = resolveActor();
            HttpServletRequest request = currentRequest();
            AuditLog logEntry = AuditLog.builder()
                    .serviceName(limit(serviceName, 80))
                    .module(limit(required(module, "GENERAL"), 80))
                    .actionType(limit(required(actionType, "SYSTEM"), 40))
                    .action(limit(required(action, actionType), 160))
                    .entityType(limit(required(entityType, "UNKNOWN"), 80))
                    .entityId(entityId)
                    .entityName(limit(entityName, 255))
                    .actorId(actor.id())
                    .actorName(limit(actor.name(), 120))
                    .actorEmail(limit(actor.email(), 180))
                    .reason(limit(reason, 500))
                    .beforeSnapshot(toJson(beforeSnapshot))
                    .afterSnapshot(toJson(afterSnapshot))
                    .metadata(toJson(metadata))
                    .ipAddress(limit(resolveIpAddress(request), 80))
                    .userAgent(limit(request == null ? null : request.getHeader("User-Agent"), 500))
                    .build();
            auditLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.warn("Failed to write audit log for action={} entityType={} entityId={}: {}",
                    action, entityType, entityId, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> findAll(Pageable pageable, String module, String actionType,
            String entityType, String keyword) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(module)) {
                predicates.add(cb.equal(cb.upper(root.get("module")), module.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(actionType)) {
                predicates.add(cb.equal(cb.upper(root.get("actionType")), actionType.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(entityType)) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("entityName")), like),
                        cb.like(cb.lower(root.get("actorName")), like),
                        cb.like(cb.lower(root.get("actorEmail")), like),
                        cb.like(cb.lower(root.get("reason")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<AuditLogResponse> page = auditLogRepository.findAll(spec, pageable).map(this::toResponse);
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private AuditLogResponse toResponse(AuditLog logEntry) {
        return new AuditLogResponse(
                logEntry.getId(),
                logEntry.getServiceName(),
                logEntry.getModule(),
                logEntry.getActionType(),
                logEntry.getAction(),
                logEntry.getEntityType(),
                logEntry.getEntityId(),
                logEntry.getEntityName(),
                logEntry.getActorId(),
                logEntry.getActorName(),
                logEntry.getActorEmail(),
                logEntry.getReason(),
                logEntry.getBeforeSnapshot(),
                logEntry.getAfterSnapshot(),
                logEntry.getMetadata(),
                logEntry.getIpAddress(),
                logEntry.getUserAgent(),
                logEntry.getCreatedAt());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getClass().getSimpleName() + "\"}";
        }
    }

    private Actor resolveActor() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Actor.system();
        }

        UUID headerUserId = parseUuid(request.getHeader("X-User-Id"));
        String headerUsername = firstText(request.getHeader("X-User-Name"), request.getHeader("X-Username"));
        String headerEmail = request.getHeader("X-User-Email");
        if (headerUserId != null || StringUtils.hasText(headerUsername) || StringUtils.hasText(headerEmail)) {
            return new Actor(headerUserId, defaultActorName(headerUsername, headerEmail), headerEmail);
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Actor.system();
        }

        try {
            String token = authHeader.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Actor.system();
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            UUID id = parseUuid(text(payload, "sub"));
            String username = text(payload, "username");
            String email = text(payload, "email");
            return new Actor(id, defaultActorName(username, email), email);
        } catch (Exception ex) {
            log.debug("Cannot parse audit actor from Authorization header: {}", ex.getMessage());
            return Actor.system();
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    private String required(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String defaultActorName(String username, String email) {
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        if (StringUtils.hasText(email)) {
            return email.trim();
        }
        return "system";
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record Actor(UUID id, String name, String email) {
        static Actor system() {
            return new Actor(null, "system", null);
        }
    }
}
