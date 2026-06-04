package app.SkillSync.controller;

import app.SkillSync.model.Assessment;
import app.SkillSync.model.AssessmentAssignment;
import app.SkillSync.model.AssessmentStatus;
import app.SkillSync.model.AssessmentType;
import app.SkillSync.model.AssignmentStatus;
import app.SkillSync.model.Candidate;
import app.SkillSync.repository.AssessmentAssignmentRepository;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.AssessmentTemplateRepository;
import app.SkillSync.repository.AuditLogRepository;
import app.SkillSync.repository.BillingSubscriptionRepository;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.EmailTokenRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.ProcessedWebhookEventRepository;
import app.SkillSync.repository.SubscriptionPlanRepository;
import app.SkillSync.repository.TestResultRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.service.AssessmentService;
import app.SkillSync.service.CandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentService assessmentService;

    @MockitoBean
    private CandidateService candidateService;

    @MockitoBean
    private AssessmentAssignmentRepository assessmentAssignmentRepository;

    @MockitoBean
    private AssessmentRepository assessmentRepository;

    @MockitoBean
    private AssessmentTemplateRepository assessmentTemplateRepository;

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
    void csrfEndpoint_returnsReadableXsrfCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void logout_expiresAuthCsrfAndSessionCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("skillsync_auth", 0))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0))
                .andExpect(cookie().maxAge("JSESSIONID", 0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAssessment_withoutCsrf_isForbiddenBeforeControllerServiceRuns() throws Exception {
        mockMvc.perform(post("/api/assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssessmentJson()))
                .andExpect(status().isForbidden());

        verify(assessmentService, never()).createAssessment(any());
    }

    @Test
    @WithMockUser(roles = "CANDIDATE")
    void createAssessment_withCandidateRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/assessments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssessmentJson()))
                .andExpect(status().isForbidden());

        verify(assessmentService, never()).createAssessment(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAssessment_withAdminRoleAndCsrf_isAllowed() throws Exception {
        Assessment assessment = new Assessment();
        assessment.setId("assessment-1");
        assessment.setTitle("Java Basics");
        assessment.setType(AssessmentType.MCQ);
        assessment.setStatus(AssessmentStatus.PUBLISHED);
        assessment.setMaxScore(100);

        when(assessmentService.createAssessment(any())).thenReturn(assessment);

        mockMvc.perform(post("/api/assessments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssessmentJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("assessment-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void submitAssignment_withAdminRole_isForbiddenBeforeServiceRuns() throws Exception {
        mockMvc.perform(post("/api/assessments/assignments/assignment-1/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitAssignmentJson()))
                .andExpect(status().isForbidden());

        verify(assessmentService, never()).submitAssignment(any(), any());
    }

    @Test
    @WithMockUser(roles = "CANDIDATE")
    void submitAssignment_withCandidateRoleAndCsrf_isAllowed() throws Exception {
        AssessmentAssignment assignment = assignment("assignment-1");

        when(assessmentService.submitAssignment(any(), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/assessments/assignments/assignment-1/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitAssignmentJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("assignment-1"));
    }

    @Test
    @WithMockUser(roles = "RECRUITER")
    void gradeAssignment_withRecruiterRole_isForbiddenBeforeServiceRuns() throws Exception {
        mockMvc.perform(patch("/api/assessments/assignments/assignment-1/grade")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeAssignmentJson()))
                .andExpect(status().isForbidden());

        verify(assessmentService, never()).gradeAssignment(any(), any());
    }

    @Test
    @WithMockUser(roles = "EVALUATOR")
    void gradeAssignment_withEvaluatorRoleAndCsrf_isAllowed() throws Exception {
        AssessmentAssignment assignment = assignment("assignment-1");
        assignment.setScore(80);

        when(assessmentService.gradeAssignment(any(), any())).thenReturn(assignment);

        mockMvc.perform(patch("/api/assessments/assignments/assignment-1/grade")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeAssignmentJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80));
    }

    @Test
    @WithMockUser(roles = "CANDIDATE")
    void listCandidates_withCandidateRole_isForbiddenBeforeServiceRuns() throws Exception {
        mockMvc.perform(get("/api/candidates"))
                .andExpect(status().isForbidden());

        verify(candidateService, never()).getAllCandidates();
    }

    @Test
    @WithMockUser(roles = "RECRUITER")
    void createCandidate_withRecruiterRoleAndCsrf_isAllowed() throws Exception {
        Candidate candidate = new Candidate();
        candidate.setId("candidate-1");
        candidate.setName("Ada Candidate");
        candidate.setEmail("candidate@example.com");

        when(candidateService.createCandidate(any())).thenReturn(candidate);

        mockMvc.perform(post("/api/candidates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCandidateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("candidate-1"));
    }

    private String validAssessmentJson() {
        return """
                {
                  "title": "Java Basics",
                  "type": "MCQ",
                  "maxScore": 100,
                  "prompt": "Answer the Java questions."
                }
                """;
    }

    private AssessmentAssignment assignment(String id) {
        AssessmentAssignment assignment = new AssessmentAssignment();
        assignment.setId(id);
        assignment.setStatus(AssignmentStatus.SUBMITTED);
        return assignment;
    }

    private String submitAssignmentJson() {
        return """
                {
                  "submittedAnswer": "Candidate answer"
                }
                """;
    }

    private String gradeAssignmentJson() {
        return """
                {
                  "score": 80,
                  "feedback": "Strong submission."
                }
                """;
    }

    private String createCandidateJson() {
        return """
                {
                  "name": "Ada Candidate",
                  "email": "candidate@example.com"
                }
                """;
    }
}
