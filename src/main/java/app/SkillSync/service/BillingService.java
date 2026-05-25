package app.SkillSync.service;

import app.SkillSync.dto.BillingCheckoutSessionRequest;
import app.SkillSync.dto.BillingSessionResponse;
import app.SkillSync.dto.BillingSubscriptionResponse;
import app.SkillSync.dto.SubscriptionPlanResponse;
import app.SkillSync.model.BillingSubscription;
import app.SkillSync.model.SubscriptionFeatures;
import app.SkillSync.model.SubscriptionPlan;
import app.SkillSync.model.User;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.BillingSubscriptionRepository;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.SubscriptionPlanRepository;
import app.SkillSync.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingService {

    private final BillingSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AssessmentRepository assessmentRepository;
    private final CandidateRepository candidateRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AuditLogService auditLogService;

    @Value("${billing.enabled:false}")
    private boolean billingEnabled;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public BillingService(BillingSubscriptionRepository subscriptionRepository,
                          UserRepository userRepository,
                          AssessmentRepository assessmentRepository,
                          CandidateRepository candidateRepository,
                          SubscriptionPlanRepository subscriptionPlanRepository,
                          AuditLogService auditLogService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.assessmentRepository = assessmentRepository;
        this.candidateRepository = candidateRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.auditLogService = auditLogService;
    }

    // ===================== Public Methods =====================

    public BillingSubscriptionResponse getCurrentSubscription() {
        User user = getCurrentUser();
        String orgId = requireOrganizationId(user);
        BillingSubscription subscription = getOrCreateFreeSubscription(orgId);
        return buildResponse(subscription, orgId);
    }

    public List<SubscriptionPlanResponse> listPlans() {
        return subscriptionPlanRepository.findAll()
                .stream()
                .filter(plan -> !Boolean.FALSE.equals(plan.getActive()))
                .sorted(Comparator
                        .comparing(
                                (SubscriptionPlan plan) -> plan.getDisplayOrder() == null
                                        ? Integer.MAX_VALUE
                                        : plan.getDisplayOrder()
                        )
                        .thenComparing(plan -> planKey(plan), String.CASE_INSENSITIVE_ORDER))
                .map(this::toPlanResponse)
                .toList();
    }

    public BillingSessionResponse createCheckoutSession(BillingCheckoutSessionRequest request) {
        ensureStripeConfigured();

        SubscriptionPlan plan = requirePlan(request.getPlanId());
        if (Boolean.TRUE.equals(plan.getIsFree())) {
            throw new IllegalArgumentException("Free plan does not require checkout.");
        }

        String priceId = plan.getStripePriceId();
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalArgumentException("Stripe price is not configured for plan: " + planKey(plan));
        }

        User user = getCurrentUser();
        String organizationId = requireOrganizationId(user);
        BillingSubscription localSubscription = getOrCreateFreeSubscription(organizationId);
        String customerId = ensureStripeCustomer(localSubscription, user);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(defaultIfBlank(request.getSuccessUrl(), frontendBaseUrl + "/admin?billing=success"))
                    .setCancelUrl(defaultIfBlank(request.getCancelUrl(), frontendBaseUrl + "/pricing?billing=cancelled"))
                    .putMetadata("organizationId", organizationId)
                    .putMetadata("planId", planKey(plan))
                    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                    .build();

            Session session = Session.create(params);
            auditLogService.record(
                    user,
                    "BILLING_CHECKOUT_STARTED",
                    "SUBSCRIPTION_PLAN",
                    planKey(plan),
                    Map.of("planId", planKey(plan))
            );
            return new BillingSessionResponse(session.getUrl());
        } catch (StripeException e) {
            throw new RuntimeException("Unable to create Stripe checkout session.", e);
        }
    }

    public BillingSessionResponse createCustomerPortalSession() {
        ensureStripeConfigured();

        User user = getCurrentUser();
        String organizationId = requireOrganizationId(user);
        BillingSubscription localSubscription = getOrCreateFreeSubscription(organizationId);
        String customerId = ensureStripeCustomer(localSubscription, user);

        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .setReturnUrl(frontendBaseUrl + "/admin")
                            .build();

            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params);

            auditLogService.record(
                    user,
                    "BILLING_PORTAL_OPENED",
                    "BILLING_SUBSCRIPTION",
                    localSubscription.getId(),
                    Map.of("customerConfigured", customerId != null && !customerId.isBlank())
            );

            return new BillingSessionResponse(session.getUrl());
        } catch (StripeException e) {
            throw new RuntimeException("Unable to open Stripe billing portal.", e);
        }
    }

    // ===================== Feature Checks =====================

    public void ensureCanCreateAssessment(String organizationId) {
        UsageLimit limit = getUsageLimit(organizationId, "activeAssessments");
        if (limit.isAtLimit()) throw new IllegalArgumentException("Your plan has reached active assessment limit.");
    }

    public void ensureCanInviteCandidate(String organizationId) {
        UsageLimit limit = getUsageLimit(organizationId, "candidateInvites");
        if (limit.isAtLimit()) throw new IllegalArgumentException("Your plan has reached candidate invite limit.");
    }

    public void ensureCanInviteTeamMember(String organizationId) {
        UsageLimit limit = getUsageLimit(organizationId, "teamMembers");
        if (limit.isAtLimit()) throw new IllegalArgumentException("Your plan has reached team member limit.");
    }

    public void ensureFeatureAccess(String organizationId, String feature) {
        SubscriptionPlan plan = requireCurrentPlan(getOrCreateFreeSubscription(organizationId));
        SubscriptionFeatures features = featuresOrDefault(plan);
        boolean hasAccess = switch (feature) {
            case "aiGeneration" -> Boolean.TRUE.equals(features.getAiGeneration());
            case "proctoring" -> Boolean.TRUE.equals(features.getProctoring());
            case "branding" -> Boolean.TRUE.equals(features.getBranding());
            default -> true;
        };
        if (!hasAccess) throw new IllegalArgumentException(feature + " is not available on your current plan.");
    }

    // ===================== Usage / Limit Helpers =====================

    private UsageLimit getUsageLimit(String organizationId, String feature) {
        BillingSubscription subscription = getOrCreateFreeSubscription(organizationId);
        SubscriptionPlan plan = requireCurrentPlan(subscription);
        SubscriptionFeatures features = featuresOrDefault(plan);

        long used = switch (feature) {
            case "activeAssessments" -> assessmentRepository.countByOrganizationId(organizationId);
            case "candidateInvites" -> candidateRepository.countByOrganizationIdAndCreatedAtBetween(organizationId, monthStart(), monthEnd());
            case "teamMembers" -> userRepository.findByOrganizationId(organizationId)
                    .stream()
                    .filter(user -> user.getRole() != null && user.getRole().isOrganizationStaff())
                    .filter(User::isActiveForLogin)
                    .count();
            default -> 0;
        };

        Long limit = switch (feature) {
            case "activeAssessments" -> features.getActiveAssessments();
            case "candidateInvites" -> features.getCandidateInvites();
            case "teamMembers" -> features.getTeamMembers();
            default -> null;
        };

        return new UsageLimit(used, limit);
    }

    private record UsageLimit(long used, Long limit) {
        private boolean isAtLimit() { return limit != null && used >= limit; }
    }

    // ===================== Billing Subscription Helpers =====================

    private BillingSubscription getOrCreateFreeSubscription(String organizationId) {
        return subscriptionRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    BillingSubscription sub = new BillingSubscription();
                    sub.setOrganizationId(organizationId);
                    sub.setPlanId(planKey(requireFreePlan()));
                    sub.setStatus("FREE");
                    sub.setCreatedAt(Instant.now());
                    sub.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(sub);
                });
    }

    private String planKey(SubscriptionPlan plan) {
        String key = plan.getCode();
        if (key == null || key.isBlank()) key = plan.getName();
        if (key == null || key.isBlank()) throw new IllegalStateException("Subscription plan must have code or name.");
        return key.trim().toLowerCase(Locale.ROOT);
    }

    // ===================== Other Helpers =====================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found."));
    }

    private String requireOrganizationId(User user) {
        String orgId = user.getOrganizationId();
        if (orgId == null || orgId.isBlank()) throw new RuntimeException("User is not linked to an organization.");
        return orgId;
    }

    private Instant monthStart() { return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC); }

    private Instant monthEnd() { return monthStart().atZone(ZoneOffset.UTC).plusMonths(1).toInstant(); }

    private String defaultIfBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    // ===================== Stripe Helpers =====================

    private void ensureStripeConfigured() {
        if (!billingEnabled) throw new IllegalArgumentException("Billing not enabled.");
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) throw new IllegalArgumentException("Stripe secret key not configured.");
        Stripe.apiKey = stripeSecretKey;
    }

    private String ensureStripeCustomer(BillingSubscription sub, User user) {
        if (sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isBlank()) return sub.getStripeCustomerId();

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getFullName())
                    .putMetadata("organizationId", sub.getOrganizationId())
                    .build();

            Customer customer = Customer.create(params);
            sub.setStripeCustomerId(customer.getId());
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);

            return customer.getId();
        } catch (StripeException e) {
            throw new RuntimeException("Unable to create Stripe customer.", e);
        }
    }

    // ===================== Webhook Handling =====================

    public void handleStripeWebhook(String payload, String sigHeader) {
        Event event;
        try { event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret); }
        catch (SignatureVerificationException e) { throw new IllegalArgumentException("Invalid Stripe webhook signature."); }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> handleSubscriptionEvent(event);
        }
    }

    private void handleCheckoutCompleted(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof Session session)) return;

        String orgId = session.getMetadata().get("organizationId");
        String planId = session.getMetadata().get("planId");
        if (orgId == null || planId == null) return;

        BillingSubscription sub = getOrCreateFreeSubscription(orgId);
        SubscriptionPlan plan = requirePlan(planId);

        sub.setPlanId(planKey(plan));
        sub.setStripeCustomerId(session.getCustomer());
        sub.setStripeSubscriptionId(session.getSubscription());
        sub.setStatus("ACTIVE");
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
        auditLogService.recordForOrganization(
                null,
                orgId,
                "BILLING_CHECKOUT_COMPLETED",
                "BILLING_SUBSCRIPTION",
                sub.getId(),
                Map.of(
                        "planId", planKey(plan),
                        "hasStripeSubscription", session.getSubscription() != null
                )
        );
    }

    private void handleSubscriptionEvent(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof Subscription stripeSub)) return;

        BillingSubscription sub = subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .or(() -> subscriptionRepository.findByStripeCustomerId(stripeSub.getCustomer()))
                .orElse(null);
        if (sub == null) return;

        String stripePriceId = stripeSub.getItems().getData().get(0).getPrice().getId();
        SubscriptionPlan plan = subscriptionPlanRepository.findByStripePriceId(stripePriceId)
                .orElse(requireFreePlan());

        sub.setPlanId(planKey(plan));
        sub.setStatus(stripeSub.getStatus() == null ? "ACTIVE" : stripeSub.getStatus().toUpperCase(Locale.ROOT));
        Long end = stripeSub.getItems().getData().get(0).getCurrentPeriodEnd();
        if (end != null) sub.setBillingPeriodEndsAt(Instant.ofEpochSecond(end));
        sub.setUpdatedAt(Instant.now());

        subscriptionRepository.save(sub);
        auditLogService.recordForOrganization(
                null,
                sub.getOrganizationId(),
                "BILLING_SUBSCRIPTION_UPDATED",
                "BILLING_SUBSCRIPTION",
                sub.getId(),
                Map.of("planId", planKey(plan), "status", sub.getStatus())
        );
    }

    private SubscriptionPlan requirePlan(String planId) {
        if (planId == null || planId.isBlank()) throw new IllegalArgumentException("Subscription plan is required.");
        return subscriptionPlanRepository.findFirstByCodeIgnoreCase(planId)
                .or(() -> subscriptionPlanRepository.findFirstByNameIgnoreCase(planId))
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found: " + planId));
    }

    private SubscriptionPlan requireFreePlan() {
        return subscriptionPlanRepository.findFirstByIsFreeTrue()
                .orElseThrow(() -> new IllegalStateException("No free subscription plan configured."));
    }

    private SubscriptionPlan requireCurrentPlan(BillingSubscription subscription) {
        if (!isSubscriptionEntitled(subscription)) return requireFreePlan();
        if (subscription.getPlanId() == null || subscription.getPlanId().isBlank()) return requireFreePlan();
        return subscriptionPlanRepository.findFirstByCodeIgnoreCase(subscription.getPlanId())
                .or(() -> subscriptionPlanRepository.findFirstByNameIgnoreCase(subscription.getPlanId()))
                .orElseThrow(() -> new IllegalStateException("Subscription plan not configured: " + subscription.getPlanId()));
    }

    private boolean isSubscriptionEntitled(BillingSubscription subscription) {
        String status = subscription.getStatus();
        if (status == null || status.isBlank()) {
            return true;
        }

        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        return normalizedStatus.equals("FREE") ||
                normalizedStatus.equals("ACTIVE") ||
                normalizedStatus.equals("TRIALING");
    }

    private Map<String, Object> buildUsage(String orgId, SubscriptionPlan plan) {
        SubscriptionFeatures f = featuresOrDefault(plan);
        Map<String, Object> usage = new HashMap<>();
        usage.put("activeAssessments", assessmentRepository.countByOrganizationId(orgId));
        usage.put("candidateInvites", candidateRepository.countByOrganizationIdAndCreatedAtBetween(orgId, monthStart(), monthEnd()));
        usage.put(
                "teamMembers",
                userRepository.findByOrganizationId(orgId)
                        .stream()
                        .filter(user -> user.getRole() != null && user.getRole().isOrganizationStaff())
                        .filter(User::isActiveForLogin)
                        .count()
        );
        usage.put("aiGeneration", f.getAiGeneration());
        usage.put("proctoring", f.getProctoring());
        usage.put("branding", f.getBranding());
        return usage;
    }
    private BillingSubscriptionResponse buildResponse(BillingSubscription subscription, String organizationId) {
        SubscriptionPlan plan = requireCurrentPlan(subscription);
        BillingSubscriptionResponse response = new BillingSubscriptionResponse();
        response.setPlanId(planKey(plan));
        response.setStatus(subscription.getStatus() == null ? "FREE" : subscription.getStatus());
        response.setBillingPeriodEndsAt(subscription.getBillingPeriodEndsAt());
        response.setUsage(buildUsage(organizationId, plan));
        response.setPlan(toPlanResponse(plan));
        return response;
    }
    public void ensureCanUseAiGeneration(String organizationId) {
        SubscriptionPlan plan = requireCurrentPlan(getOrCreateFreeSubscription(organizationId));
        SubscriptionFeatures features = featuresOrDefault(plan);

        if (!Boolean.TRUE.equals(features.getAiGeneration())) {
            throw new IllegalArgumentException("AI generation feature is not available on your current plan.");
        }
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
        response.setFeatures(featuresOrDefault(plan));
        response.setHighlights(plan.getHighlights());
        response.setRecommended(plan.getRecommended());
        response.setActive(plan.getActive());
        response.setIsFree(plan.getIsFree());
        response.setDisplayOrder(plan.getDisplayOrder());
        return response;
    }

    private SubscriptionFeatures featuresOrDefault(SubscriptionPlan plan) {
        return plan.getFeatures() == null ? new SubscriptionFeatures() : plan.getFeatures();
    }
}
