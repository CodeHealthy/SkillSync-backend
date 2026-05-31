package app.SkillSync;

import app.SkillSync.repository.AssessmentAssignmentRepository;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.AuditLogRepository;
import app.SkillSync.repository.BillingSubscriptionRepository;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.EmailTokenRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.ProcessedWebhookEventRepository;
import app.SkillSync.repository.SubscriptionPlanRepository;
import app.SkillSync.repository.TestResultRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
class SkillSyncApplicationTests {

    @MockitoBean
    private AssessmentAssignmentRepository assessmentAssignmentRepository;

    @MockitoBean
    private AssessmentRepository assessmentRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private BillingSubscriptionRepository billingSubscriptionRepository;

    @MockitoBean
    private CandidateRepository candidateRepository;

    @MockitoBean
    private EmailTokenRepository emailTokenRepository;

    @MockitoBean
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @MockitoBean
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @MockitoBean
    private TestResultRepository testResultRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoTemplate mongoTemplate;

    @Test
    void contextLoads() {
    }
}
