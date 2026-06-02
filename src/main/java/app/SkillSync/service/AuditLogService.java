package app.SkillSync.service;

import app.SkillSync.dto.AuditLogResponse;
import app.SkillSync.model.AuditLog;
import app.SkillSync.model.User;
import app.SkillSync.repository.AuditLogRepository;
import app.SkillSync.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AuditLogService {

    private static final int MAX_METADATA_VALUE_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 300;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void record(
            User actor,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> metadata
    ) {
        if (actor == null) {
            recordUnauthenticated(action, targetType, targetId, null, metadata);
            return;
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actor.getId());
        auditLog.setActorEmail(actor.getEmail());
        auditLog.setActorRole(actor.getRole());
        auditLog.setOrganizationId(actor.getOrganizationId());
        auditLog.setAction(normalizeAction(action));
        auditLog.setTargetType(normalizeTargetType(targetType));
        auditLog.setTargetId(trimToNull(targetId));
        auditLog.setMetadata(sanitizeMetadata(metadata));
        applyRequestContext(auditLog);
        saveQuietly(auditLog);
    }

    public void recordForOrganization(
            User actor,
            String organizationId,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> metadata
    ) {
        AuditLog auditLog = new AuditLog();

        if (actor != null) {
            auditLog.setActorUserId(actor.getId());
            auditLog.setActorEmail(actor.getEmail());
            auditLog.setActorRole(actor.getRole());
        }

        auditLog.setOrganizationId(trimToNull(organizationId));
        auditLog.setAction(normalizeAction(action));
        auditLog.setTargetType(normalizeTargetType(targetType));
        auditLog.setTargetId(trimToNull(targetId));
        auditLog.setMetadata(sanitizeMetadata(metadata));
        applyRequestContext(auditLog);
        saveQuietly(auditLog);
    }

    public void recordUnauthenticated(
            String action,
            String targetType,
            String targetId,
            String actorEmail,
            Map<String, Object> metadata
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorEmail(trimToNull(actorEmail));
        auditLog.setAction(normalizeAction(action));
        auditLog.setTargetType(normalizeTargetType(targetType));
        auditLog.setTargetId(trimToNull(targetId));
        auditLog.setMetadata(sanitizeMetadata(metadata));
        applyRequestContext(auditLog);
        saveQuietly(auditLog);
    }

    public List<AuditLogResponse> listOrganizationLogs(
            Authentication authentication,
            String action
    ) {
        User user = requireUser(authentication);

        if (user.getRole() == null || !user.getRole().isOrganizationAdmin()) {
            throw new IllegalArgumentException("Only organization admins can view audit logs.");
        }

        String organizationId = requireOrganizationId(user);
        String normalizedAction = trimToNull(action);

        List<AuditLog> logs = normalizedAction == null
                ? auditLogRepository.findTop100ByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : auditLogRepository.findTop100ByOrganizationIdAndActionOrderByCreatedAtDesc(
                        organizationId,
                        normalizeAction(normalizedAction)
                );

        return logs.stream().map(this::toResponse).toList();
    }

    public List<AuditLogResponse> listPlatformLogs(
            Authentication authentication,
            String action,
            String organizationId,
            String actorEmail,
            String targetType
    ) {
        User user = requireUser(authentication);

        if (user.getRole() == null || !user.getRole().isPlatformAdmin()) {
            throw new IllegalArgumentException("Only platform super admins can view platform audit logs.");
        }

        List<AuditLog> logs = findPlatformLogs(action, organizationId, actorEmail, targetType);

        return logs.stream().map(this::toResponse).toList();
    }

    private List<AuditLog> findPlatformLogs(
            String action,
            String organizationId,
            String actorEmail,
            String targetType
    ) {
        String normalizedAction = trimToNull(action);
        String normalizedOrganizationId = trimToNull(organizationId);
        String normalizedActorEmail = trimToNull(actorEmail);
        String normalizedTargetType = trimToNull(targetType);

        if (normalizedOrganizationId == null &&
                normalizedActorEmail == null &&
                normalizedTargetType == null) {
            return normalizedAction == null
                    ? auditLogRepository.findTop200ByOrderByCreatedAtDesc()
                    : auditLogRepository.findTop200ByActionOrderByCreatedAtDesc(
                            normalizeAction(normalizedAction)
                    );
        }

        List<Criteria> filters = new ArrayList<>();

        if (normalizedAction != null) {
            filters.add(Criteria.where("action").is(normalizeAction(normalizedAction)));
        }

        if (normalizedOrganizationId != null) {
            filters.add(Criteria.where("organizationId").is(normalizedOrganizationId));
        }

        if (normalizedActorEmail != null) {
            filters.add(Criteria.where("actorEmail").regex(
                    "^" + Pattern.quote(normalizedActorEmail) + "$",
                    "i"
            ));
        }

        if (normalizedTargetType != null) {
            filters.add(Criteria.where("targetType").is(normalizeTargetType(normalizedTargetType)));
        }

        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(200);

        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }

        return mongoTemplate.find(query, AuditLog.class);
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private String requireOrganizationId(User user) {
        if (user.getOrganizationId() == null || user.getOrganizationId().isBlank()) {
            throw new IllegalArgumentException("User is not linked to an organization.");
        }

        return user.getOrganizationId();
    }

    private void applyRequestContext(AuditLog auditLog) {
        auditLog.setCreatedAt(Instant.now());

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        auditLog.setIpAddress(resolveClientIp(request));
        auditLog.setUserAgent(truncate(trimToNull(request.getHeader("User-Agent")), MAX_USER_AGENT_LENGTH));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));

        if (forwardedFor != null) {
            int commaIndex = forwardedFor.indexOf(",");
            return commaIndex >= 0
                    ? forwardedFor.substring(0, commaIndex).trim()
                    : forwardedFor;
        }

        return request.getRemoteAddr();
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();

        metadata.forEach((key, value) -> {
            String safeKey = trimToNull(key);

            if (safeKey == null || isSensitiveKey(safeKey)) {
                return;
            }

            sanitized.put(safeKey, sanitizeValue(value));
        });

        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Iterable<?> iterable) {
            return sanitizeIterable(iterable);
        }

        return truncate(String.valueOf(value), MAX_METADATA_VALUE_LENGTH);
    }

    private List<String> sanitizeIterable(Iterable<?> iterable) {
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .limit(20)
                .map(value -> truncate(String.valueOf(value), MAX_METADATA_VALUE_LENGTH))
                .toList();
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase();

        return normalized.contains("password") ||
                normalized.contains("token") ||
                normalized.contains("secret") ||
                normalized.contains("key") ||
                normalized.contains("authorization");
    }

    private void saveQuietly(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (RuntimeException ignored) {
            // Audit logging must never block the user workflow.
        }
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(auditLog.getId());
        response.setActorUserId(auditLog.getActorUserId());
        response.setActorEmail(auditLog.getActorEmail());
        response.setActorRole(auditLog.getActorRole());
        response.setOrganizationId(auditLog.getOrganizationId());
        response.setTargetType(auditLog.getTargetType());
        response.setTargetId(auditLog.getTargetId());
        response.setAction(auditLog.getAction());
        response.setMetadata(auditLog.getMetadata());
        response.setIpAddress(auditLog.getIpAddress());
        response.setUserAgent(auditLog.getUserAgent());
        response.setCreatedAt(auditLog.getCreatedAt());
        return response;
    }

    private String normalizeAction(String action) {
        String normalized = trimToNull(action);
        return normalized == null ? "UNKNOWN" : normalized.toUpperCase();
    }

    private String normalizeTargetType(String targetType) {
        String normalized = trimToNull(targetType);
        return normalized == null ? "SYSTEM" : normalized.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
