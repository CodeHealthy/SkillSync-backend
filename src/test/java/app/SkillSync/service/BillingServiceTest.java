package app.SkillSync.service;

import app.SkillSync.model.BillingSubscription;
import app.SkillSync.model.SubscriptionPlan;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.BillingSubscriptionRepository;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.ProcessedWebhookEventRepository;
import app.SkillSync.repository.SubscriptionPlanRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillingSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private OrganizationAccessService organizationAccessService;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                subscriptionRepository,
                userRepository,
                assessmentRepository,
                candidateRepository,
                subscriptionPlanRepository,
                processedWebhookEventRepository,
                auditLogService,
                organizationAccessService
        );
    }

    @Test
    void ensureCanCreateAssessment_usesActiveAssessmentLimitFromPlanFeatureMap() {
        mockActiveSubscription(planWithFeatures(Map.of("activeAssessments", 2)));
        when(assessmentRepository.countByOrganizationId("org-1")).thenReturn(1L);

        assertDoesNotThrow(() -> billingService.ensureCanCreateAssessment("org-1"));
    }

    @Test
    void ensureCanInviteCandidate_blocksWhenCandidateInviteLimitFromPlanFeatureMapIsReached() {
        mockActiveSubscription(planWithFeatures(Map.of("candidateInvites", 1)));
        when(candidateRepository.countByOrganizationIdAndCreatedAtBetween(
                org.mockito.ArgumentMatchers.eq("org-1"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> billingService.ensureCanInviteCandidate("org-1")
        );
    }

    @Test
    void ensureCanUseAiGeneration_usesBooleanFeatureFromPlanFeatureMap() {
        mockActiveSubscription(planWithFeatures(Map.of("aiGeneration", true)));

        assertDoesNotThrow(() -> billingService.ensureCanUseAiGeneration("org-1"));
    }

    @Test
    void ensureFeatureAccess_deniesUnknownFeatureWhenPlanFeatureMapDoesNotContainIt() {
        mockActiveSubscription(planWithFeatures(Map.of("aiGeneration", true)));

        assertThrows(
                IllegalArgumentException.class,
                () -> billingService.ensureFeatureAccess("org-1", "videoInterviewAnalysis")
        );
    }

    @Test
    void ensureCanInviteTeamMember_supportsNumericStringLimitsFromPlanFeatureMap() {
        mockActiveSubscription(planWithFeatures(Map.of("teamMembers", "2")));
        when(userRepository.findByOrganizationId("org-1")).thenReturn(java.util.List.of());

        assertDoesNotThrow(() -> billingService.ensureCanInviteTeamMember("org-1"));
    }

    private void mockActiveSubscription(SubscriptionPlan plan) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setOrganizationId("org-1");
        subscription.setPlanId("growth");
        subscription.setStatus("ACTIVE");

        when(subscriptionRepository.findByOrganizationId("org-1"))
                .thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findFirstByCodeIgnoreCase("growth"))
                .thenReturn(Optional.of(plan));
    }

    private SubscriptionPlan planWithFeatures(Map<String, Object> features) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("growth");
        plan.setName("Growth");
        plan.setFeatures(features);
        return plan;
    }
}
