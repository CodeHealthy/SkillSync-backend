package app.SkillSync.service;

import app.SkillSync.dto.PlatformAdminSummaryResponse;
import app.SkillSync.dto.PlatformOrganizationResponse;
import app.SkillSync.dto.PlatformSubscriptionPlanRequest;
import app.SkillSync.dto.PlatformUserResponse;
import app.SkillSync.dto.SubscriptionPlanResponse;
import app.SkillSync.model.Organization;
import app.SkillSync.model.SubscriptionPlan;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.SubscriptionPlanRepository;
import app.SkillSync.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformAdminService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AuditLogService auditLogService;

    public PlatformAdminService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            AuditLogService auditLogService
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.auditLogService = auditLogService;
    }

    public PlatformAdminSummaryResponse getSummary(Authentication authentication) {
        User superAdmin = requireSuperAdmin(authentication);

        List<User> users = userRepository.findAll();

        List<PlatformOrganizationResponse> organizations = organizationRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(Organization::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(organization -> toOrganizationResponse(organization, users))
                .toList();

        List<PlatformUserResponse> userResponses = users
                .stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toUserResponse)
                .toList();

        auditLogService.record(
                superAdmin,
                "PLATFORM_SUMMARY_VIEWED",
                "PLATFORM",
                "summary",
                Map.of(
                        "organizationCount", organizations.size(),
                        "userCount", userResponses.size()
                )
        );

        return new PlatformAdminSummaryResponse(organizations, userResponses);
    }

    public List<SubscriptionPlanResponse> listSubscriptionPlans(Authentication authentication) {
        requireSuperAdmin(authentication);

        return subscriptionPlanRepository.findAll()
                .stream()
                .sorted(Comparator
                        .comparing(
                                (SubscriptionPlan plan) -> plan.getDisplayOrder() == null
                                        ? Integer.MAX_VALUE
                                        : plan.getDisplayOrder()
                        )
                        .thenComparing(this::planKey, String.CASE_INSENSITIVE_ORDER))
                .map(this::toPlanResponse)
                .toList();
    }

    public SubscriptionPlanResponse createSubscriptionPlan(
            Authentication authentication,
            PlatformSubscriptionPlanRequest request
    ) {
        User superAdmin = requireSuperAdmin(authentication);
        String code = normalizePlanCode(request.getCode());

        subscriptionPlanRepository.findFirstByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Subscription plan code already exists.");
        });

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCreated(Instant.now().toString());
        applyPlanRequest(plan, request, code);

        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.record(
                superAdmin,
                "PLATFORM_SUBSCRIPTION_PLAN_CREATED",
                "SUBSCRIPTION_PLAN",
                planKey(saved),
                Map.of("planCode", planKey(saved))
        );

        return toPlanResponse(saved);
    }

    public SubscriptionPlanResponse updateSubscriptionPlan(
            Authentication authentication,
            String planId,
            PlatformSubscriptionPlanRequest request
    ) {
        User superAdmin = requireSuperAdmin(authentication);
        SubscriptionPlan plan = requirePlan(planId);
        String code = normalizePlanCode(request.getCode());

        subscriptionPlanRepository.findFirstByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(plan.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Subscription plan code already exists.");
                });

        applyPlanRequest(plan, request, code);

        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.record(
                superAdmin,
                "PLATFORM_SUBSCRIPTION_PLAN_UPDATED",
                "SUBSCRIPTION_PLAN",
                planKey(saved),
                Map.of("planCode", planKey(saved))
        );

        return toPlanResponse(saved);
    }

    public SubscriptionPlanResponse deactivateSubscriptionPlan(
            Authentication authentication,
            String planId
    ) {
        User superAdmin = requireSuperAdmin(authentication);
        SubscriptionPlan plan = requirePlan(planId);

        if (Boolean.TRUE.equals(plan.getIsFree())) {
            throw new IllegalArgumentException("The free plan cannot be deactivated.");
        }

        plan.setActive(false);
        plan.setUpdated(Instant.now().toString());

        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.record(
                superAdmin,
                "PLATFORM_SUBSCRIPTION_PLAN_DEACTIVATED",
                "SUBSCRIPTION_PLAN",
                planKey(saved),
                Map.of("planCode", planKey(saved))
        );

        return toPlanResponse(saved);
    }

    private User requireSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (user.getRole() == null || !user.getRole().isPlatformAdmin()) {
            throw new IllegalArgumentException("Only platform super admins can access this area.");
        }

        return user;
    }

    private SubscriptionPlan requirePlan(String planId) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("Subscription plan id is required.");
        }

        return subscriptionPlanRepository.findById(planId)
                .or(() -> subscriptionPlanRepository.findFirstByCodeIgnoreCase(planId))
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found."));
    }

    private void applyPlanRequest(
            SubscriptionPlan plan,
            PlatformSubscriptionPlanRequest request,
            String code
    ) {
        plan.setCode(code);
        plan.setName(trimToNull(request.getName()));
        plan.setDescription(trimToNull(request.getDescription()));
        plan.setPricing(request.getPricing());
        plan.setCurrency(defaultIfBlank(request.getCurrency(), "AED").toUpperCase(Locale.ROOT));
        plan.setBillingCycle(defaultIfBlank(request.getBillingCycle(), "month").toLowerCase(Locale.ROOT));
        plan.setStripePriceId(trimToNull(request.getStripePriceId()));
        plan.setFeatures(sanitizeFeatures(request.getFeatures()));
        plan.setHighlights(sanitizeHighlights(request.getHighlights()));
        plan.setRecommended(Boolean.TRUE.equals(request.getRecommended()));
        plan.setActive(!Boolean.FALSE.equals(request.getActive()));
        plan.setIsFree(Boolean.TRUE.equals(request.getIsFree()));
        plan.setDisplayOrder(request.getDisplayOrder());
        plan.setUpdated(Instant.now().toString());
    }

    private Map<String, Object> sanitizeFeatures(Map<String, Object> features) {
        if (features == null || features.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        features.forEach((key, value) -> {
            String safeKey = trimToNull(key);

            if (safeKey == null || value == null) {
                return;
            }

            if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                sanitized.put(safeKey, value);
            }
        });

        return sanitized;
    }

    private List<String> sanitizeHighlights(List<String> highlights) {
        if (highlights == null) {
            return List.of();
        }

        return highlights.stream()
                .map(this::trimToNull)
                .filter(value -> value != null && value.length() <= 160)
                .limit(12)
                .toList();
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        SubscriptionPlanResponse response = new SubscriptionPlanResponse();
        response.setId(plan.getId());
        response.setCode(planKey(plan));
        response.setName(plan.getName());
        response.setDescription(plan.getDescription());
        response.setPricing(plan.getPricing());
        response.setCurrency(plan.getCurrency());
        response.setBillingCycle(plan.getBillingCycle());
        response.setFeatures(plan.getFeatures() == null ? Map.of() : plan.getFeatures());
        response.setHighlights(plan.getHighlights() == null ? List.of() : plan.getHighlights());
        response.setRecommended(plan.getRecommended());
        response.setActive(plan.getActive());
        response.setIsFree(plan.getIsFree());
        response.setDisplayOrder(plan.getDisplayOrder());
        return response;
    }

    private String planKey(SubscriptionPlan plan) {
        String key = plan.getCode();
        if (key == null || key.isBlank()) {
            key = plan.getName();
        }

        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Subscription plan must have code or name.");
        }

        return normalizePlanCode(key);
    }

    private String normalizePlanCode(String value) {
        String normalized = trimToNull(value);

        if (normalized == null) {
            throw new IllegalArgumentException("Subscription plan code is required.");
        }

        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        return value.trim();
    }

    private PlatformOrganizationResponse toOrganizationResponse(
            Organization organization,
            List<User> users
    ) {
        long userCount = users.stream()
                .filter(user -> organization.getId() != null &&
                        organization.getId().equals(user.getOrganizationId()))
                .count();

        return new PlatformOrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCreatedAt(),
                userCount
        );
    }

    private PlatformUserResponse toUserResponse(User user) {
        return new PlatformUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getOrganizationId(),
                user.isActiveForLogin(),
                user.getCreatedAt()
        );
    }
}
