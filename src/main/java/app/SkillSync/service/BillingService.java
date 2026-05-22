package app.SkillSync.service;

import app.SkillSync.dto.BillingCheckoutSessionRequest;
import app.SkillSync.dto.BillingSessionResponse;
import app.SkillSync.dto.BillingSubscriptionResponse;
import app.SkillSync.model.BillingSubscription;
import app.SkillSync.model.User;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.BillingSubscriptionRepository;
import app.SkillSync.repository.CandidateRepository;
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
import java.util.HashMap;
import java.util.Map;

@Service
public class BillingService {

    public static final String PLAN_FREE = "free";
    public static final String PLAN_STARTER = "starter";
    public static final String PLAN_GROWTH = "growth";
    public static final String PLAN_BUSINESS = "business";

    private static final String FEATURE_ACTIVE_ASSESSMENTS = "activeAssessments";
    private static final String FEATURE_CANDIDATE_INVITES = "candidateInvites";
    private static final String FEATURE_AI_GENERATION = "aiGeneration";
    private static final String FEATURE_PROCTORING = "proctoring";
    private static final String FEATURE_BRANDING = "branding";
    private static final String FEATURE_TEAM_MEMBERS = "teamMembers";

    private final BillingSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AssessmentRepository assessmentRepository;
    private final CandidateRepository candidateRepository;

    @Value("${billing.enabled:false}")
    private boolean billingEnabled;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${stripe.price.starter:}")
    private String starterPriceId;

    @Value("${stripe.price.growth:}")
    private String growthPriceId;

    @Value("${stripe.price.business:}")
    private String businessPriceId;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public BillingService(
            BillingSubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            AssessmentRepository assessmentRepository,
            CandidateRepository candidateRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.assessmentRepository = assessmentRepository;
        this.candidateRepository = candidateRepository;
    }

    public BillingSubscriptionResponse getCurrentSubscription() {
        User user = getCurrentUser();
        String organizationId = requireOrganizationId(user);
        BillingSubscription subscription = getOrCreateFreeSubscription(organizationId);

        return toResponse(subscription, organizationId);
    }

    public BillingSessionResponse createCheckoutSession(BillingCheckoutSessionRequest request) {
        ensureStripeConfigured();

        String planId = normalizePlanId(request.getPlanId());

        if (PLAN_FREE.equals(planId)) {
            throw new IllegalArgumentException("Free plan does not require checkout.");
        }

        String priceId = priceIdForPlan(planId);

        if (priceId == null || priceId.isBlank()) {
            throw new IllegalArgumentException("Stripe price is not configured for this plan.");
        }

        User user = getCurrentUser();
        String organizationId = requireOrganizationId(user);
        BillingSubscription localSubscription = getOrCreateFreeSubscription(organizationId);
        String customerId = ensureStripeCustomer(localSubscription, user);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(defaultIfBlank(
                            request.getSuccessUrl(),
                            frontendBaseUrl + "/admin?billing=success"
                    ))
                    .setCancelUrl(defaultIfBlank(
                            request.getCancelUrl(),
                            frontendBaseUrl + "/pricing?billing=cancelled"
                    ))
                    .putMetadata("organizationId", organizationId)
                    .putMetadata("planId", planId)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);
            return new BillingSessionResponse(session.getUrl());
        } catch (StripeException exception) {
            throw new RuntimeException("Unable to create Stripe checkout session.");
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

            return new BillingSessionResponse(session.getUrl());
        } catch (StripeException exception) {
            throw new RuntimeException("Unable to open Stripe billing portal.");
        }
    }

    public void handleStripeWebhook(String payload, String signatureHeader) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook secret is not configured.");
        }

        Event event;

        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException exception) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature.");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            handleCheckoutCompleted(event);
            return;
        }

        if ("customer.subscription.updated".equals(event.getType()) ||
                "customer.subscription.created".equals(event.getType()) ||
                "customer.subscription.deleted".equals(event.getType())) {
            handleSubscriptionEvent(event);
        }
    }

    public void ensureCanCreateAssessment(String organizationId) {
        UsageLimit usageLimit = getUsageLimit(organizationId, FEATURE_ACTIVE_ASSESSMENTS);

        if (usageLimit.isAtLimit()) {
            throw new IllegalArgumentException("Your current plan has reached its active assessment limit.");
        }
    }

    public void ensureCanInviteCandidate(String organizationId) {
        UsageLimit usageLimit = getUsageLimit(organizationId, FEATURE_CANDIDATE_INVITES);

        if (usageLimit.isAtLimit()) {
            throw new IllegalArgumentException("Your current plan has reached its monthly candidate invite limit.");
        }
    }

    public void ensureCanUseAiGeneration(String organizationId) {
        if (!hasFeatureAccess(organizationId, FEATURE_AI_GENERATION)) {
            throw new IllegalArgumentException("AI assessment generation is available on paid plans.");
        }
    }

    private void handleCheckoutCompleted(Event event) {
        StripeObject stripeObject = deserializeStripeObject(event);

        if (!(stripeObject instanceof Session session)) {
            return;
        }

        String organizationId = session.getMetadata() == null
                ? null
                : session.getMetadata().get("organizationId");
        String planId = session.getMetadata() == null
                ? PLAN_FREE
                : normalizePlanId(session.getMetadata().get("planId"));

        if (organizationId == null || organizationId.isBlank()) {
            return;
        }

        BillingSubscription subscription = getOrCreateFreeSubscription(organizationId);
        subscription.setStripeCustomerId(session.getCustomer());
        subscription.setStripeSubscriptionId(session.getSubscription());
        subscription.setPlanId(planId);
        subscription.setStatus("ACTIVE");
        subscription.setUpdatedAt(Instant.now());

        subscriptionRepository.save(subscription);
    }

    private void handleSubscriptionEvent(Event event) {
        StripeObject stripeObject = deserializeStripeObject(event);

        if (!(stripeObject instanceof Subscription stripeSubscription)) {
            return;
        }

        BillingSubscription subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscription.getId())
                .or(() -> subscriptionRepository.findByStripeCustomerId(stripeSubscription.getCustomer()))
                .orElse(null);

        if (subscription == null) {
            return;
        }

        if ("customer.subscription.deleted".equals(event.getType())) {
            subscription.setPlanId(PLAN_FREE);
            subscription.setStatus("CANCELED");
        } else {
            subscription.setStatus(stripeSubscription.getStatus() == null
                    ? "ACTIVE"
                    : stripeSubscription.getStatus().toUpperCase());
            subscription.setPlanId(planIdForPrice(extractPriceId(stripeSubscription)));
        }

        Long currentPeriodEnd = extractCurrentPeriodEnd(stripeSubscription);

        if (currentPeriodEnd != null) {
            subscription.setBillingPeriodEndsAt(
                    Instant.ofEpochSecond(currentPeriodEnd)
            );
        }

        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);
    }

    private StripeObject deserializeStripeObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().orElse(null);
    }

    private BillingSubscription getOrCreateFreeSubscription(String organizationId) {
        return subscriptionRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    BillingSubscription subscription = new BillingSubscription();
                    subscription.setOrganizationId(organizationId);
                    subscription.setPlanId(PLAN_FREE);
                    subscription.setStatus("FREE");
                    subscription.setCreatedAt(Instant.now());
                    subscription.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(subscription);
                });
    }

    private BillingSubscriptionResponse toResponse(
            BillingSubscription subscription,
            String organizationId
    ) {
        BillingSubscriptionResponse response = new BillingSubscriptionResponse();
        response.setPlanId(normalizePlanId(subscription.getPlanId()));
        response.setStatus(defaultIfBlank(subscription.getStatus(), "FREE"));
        response.setBillingPeriodEndsAt(subscription.getBillingPeriodEndsAt());
        response.setUsage(buildUsage(organizationId));

        return response;
    }

    private Map<String, Object> buildUsage(String organizationId) {
        Map<String, Object> usage = new HashMap<>();
        usage.put(FEATURE_ACTIVE_ASSESSMENTS, assessmentRepository.countByOrganizationId(organizationId));
        usage.put(FEATURE_CANDIDATE_INVITES, candidateRepository.countByOrganizationIdAndCreatedAtBetween(
                organizationId,
                monthStart(),
                monthEnd()
        ));
        usage.put(FEATURE_TEAM_MEMBERS, 1);

        return usage;
    }

    private UsageLimit getUsageLimit(String organizationId, String feature) {
        BillingSubscription subscription = getOrCreateFreeSubscription(organizationId);
        String planId = normalizePlanId(subscription.getPlanId());
        Long limit = numericLimit(planId, feature);
        long used = usedForFeature(organizationId, feature);

        return new UsageLimit(used, limit);
    }

    private boolean hasFeatureAccess(String organizationId, String feature) {
        BillingSubscription subscription = getOrCreateFreeSubscription(organizationId);
        return booleanLimit(normalizePlanId(subscription.getPlanId()), feature);
    }

    private long usedForFeature(String organizationId, String feature) {
        if (FEATURE_ACTIVE_ASSESSMENTS.equals(feature)) {
            return assessmentRepository.countByOrganizationId(organizationId);
        }

        if (FEATURE_CANDIDATE_INVITES.equals(feature)) {
            return candidateRepository.countByOrganizationIdAndCreatedAtBetween(
                    organizationId,
                    monthStart(),
                    monthEnd()
            );
        }

        return 0;
    }

    private Long numericLimit(String planId, String feature) {
        if (FEATURE_ACTIVE_ASSESSMENTS.equals(feature)) {
            return switch (planId) {
                case PLAN_STARTER -> 5L;
                case PLAN_GROWTH -> 25L;
                case PLAN_BUSINESS -> null;
                default -> 1L;
            };
        }

        if (FEATURE_CANDIDATE_INVITES.equals(feature)) {
            return switch (planId) {
                case PLAN_STARTER -> 50L;
                case PLAN_GROWTH -> 250L;
                case PLAN_BUSINESS -> null;
                default -> 5L;
            };
        }

        return 0L;
    }

    private boolean booleanLimit(String planId, String feature) {
        if (FEATURE_AI_GENERATION.equals(feature) || FEATURE_PROCTORING.equals(feature)) {
            return !PLAN_FREE.equals(planId);
        }

        if (FEATURE_BRANDING.equals(feature)) {
            return PLAN_GROWTH.equals(planId) || PLAN_BUSINESS.equals(planId);
        }

        return false;
    }

    private String ensureStripeCustomer(BillingSubscription subscription, User user) {
        if (subscription.getStripeCustomerId() != null &&
                !subscription.getStripeCustomerId().isBlank()) {
            return subscription.getStripeCustomerId();
        }

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getFullName())
                    .putMetadata("organizationId", subscription.getOrganizationId())
                    .build();

            Customer customer = Customer.create(params);
            subscription.setStripeCustomerId(customer.getId());
            subscription.setUpdatedAt(Instant.now());
            subscriptionRepository.save(subscription);

            return customer.getId();
        } catch (StripeException exception) {
            throw new RuntimeException("Unable to create Stripe customer.");
        }
    }

    private void ensureStripeConfigured() {
        if (!billingEnabled) {
            throw new IllegalArgumentException("Billing is not enabled.");
        }

        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalArgumentException("Stripe secret key is not configured.");
        }

        Stripe.apiKey = stripeSecretKey;
    }

    private String priceIdForPlan(String planId) {
        return switch (planId) {
            case PLAN_STARTER -> starterPriceId;
            case PLAN_GROWTH -> growthPriceId;
            case PLAN_BUSINESS -> businessPriceId;
            default -> null;
        };
    }

    private String planIdForPrice(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return PLAN_FREE;
        }

        if (priceId.equals(starterPriceId)) {
            return PLAN_STARTER;
        }

        if (priceId.equals(growthPriceId)) {
            return PLAN_GROWTH;
        }

        if (priceId.equals(businessPriceId)) {
            return PLAN_BUSINESS;
        }

        return PLAN_FREE;
    }

    private String extractPriceId(Subscription subscription) {
        if (subscription.getItems() == null ||
                subscription.getItems().getData() == null ||
                subscription.getItems().getData().isEmpty() ||
                subscription.getItems().getData().get(0).getPrice() == null) {
            return null;
        }

        return subscription.getItems().getData().get(0).getPrice().getId();
    }

    private Long extractCurrentPeriodEnd(Subscription subscription) {
        if (subscription.getItems() == null ||
                subscription.getItems().getData() == null ||
                subscription.getItems().getData().isEmpty()) {
            return null;
        }

        return subscription.getItems().getData().get(0).getCurrentPeriodEnd();
    }

    private String normalizePlanId(String planId) {
        if (planId == null || planId.isBlank()) {
            return PLAN_FREE;
        }

        String normalized = planId.trim().toLowerCase();

        return switch (normalized) {
            case PLAN_STARTER, PLAN_GROWTH, PLAN_BUSINESS -> normalized;
            default -> PLAN_FREE;
        };
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found."));
    }

    private String requireOrganizationId(User user) {
        String organizationId = user.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("User is not linked to an organization.");
        }

        return organizationId;
    }

    private Instant monthStart() {
        return LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private Instant monthEnd() {
        return monthStart()
                .atZone(ZoneOffset.UTC)
                .plusMonths(1)
                .toInstant();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record UsageLimit(long used, Long limit) {
        private boolean isAtLimit() {
            return limit != null && used >= limit;
        }
    }
}
