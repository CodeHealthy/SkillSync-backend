package app.SkillSync.service;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.dto.QuestionReviewRequest;
import app.SkillSync.dto.RecordIntegrityEventRequest;
import app.SkillSync.dto.RunCodeRequest;
import app.SkillSync.dto.SaveAssignmentDraftRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.model.*;
import app.SkillSync.repository.AssessmentAssignmentRepository;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentServiceTest {

    private AssessmentRepository assessmentRepository;
    private AssessmentAssignmentRepository assignmentRepository;
    private CandidateRepository candidateRepository;
    private CodeExecutionService codeExecutionService;
    private UserRepository userRepository;
    private OrganizationRepository organizationRepository;
    private BillingService billingService;
    private AuditLogService auditLogService;
    private MongoTemplate mongoTemplate;
    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentRepository = mock(AssessmentRepository.class);
        assignmentRepository = mock(AssessmentAssignmentRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        codeExecutionService = mock(CodeExecutionService.class);
        userRepository = mock(UserRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        billingService = mock(BillingService.class);
        auditLogService = mock(AuditLogService.class);
        mongoTemplate = mock(MongoTemplate.class);

        assessmentService = new AssessmentService(
                assessmentRepository,
                assignmentRepository,
                candidateRepository,
                codeExecutionService,
                userRepository,
                organizationRepository,
                billingService,
                auditLogService,
                mongoTemplate
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignAssessment_whenCandidateBelongsToAnotherOrganization_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        Assessment assessment = assessment("assessment-1", "org-1");
        Candidate candidate = candidate("candidate-1", "org-2");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> assessmentService.assignAssessment(request)
        );

        assertEquals("Candidate does not belong to your organization.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void assignAssessment_whenAssessmentBelongsToAnotherOrganization_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        Assessment assessment = assessment("assessment-1", "org-2");
        Candidate candidate = candidate("candidate-1", "org-1");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> assessmentService.assignAssessment(request)
        );

        assertEquals("Assessment does not belong to your organization.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void assignAssessment_whenValid_createsOrganizationScopedAssignment() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        Assessment assessment = assessment("assessment-1", "org-1");
        Instant publishedAt = Instant.now().minusSeconds(300);
        assessment.setVersion(3);
        assessment.setPublishedAt(publishedAt);
        Candidate candidate = candidate("candidate-1", "org-1");

        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setName("SkillSync Demo Org");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(assignmentRepository.existsByAssessmentIdAndCandidateId("assessment-1", "candidate-1"))
                .thenReturn(false);
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");
        Instant dueAt = Instant.now().plusSeconds(86_400);
        request.setDueAt(dueAt);
        request.setTimeLimitMinutes(90);

        AssessmentAssignment savedAssignment = assessmentService.assignAssessment(request);

        assertEquals("assessment-1", savedAssignment.getAssessmentId());
        assertEquals("candidate-1", savedAssignment.getCandidateId());
        assertEquals("org-1", savedAssignment.getOrganizationId());
        assertEquals("SkillSync Demo Org", savedAssignment.getOrganizationName());
        assertEquals(AssignmentStatus.ASSIGNED, savedAssignment.getStatus());
        assertEquals("NOT_RUN", savedAssignment.getExecutionStatus());
        assertEquals(AssessmentType.CODING_CHALLENGE, savedAssignment.getAssessmentType());
        assertEquals(3, savedAssignment.getAssessmentVersion());
        assertEquals(publishedAt, savedAssignment.getAssessmentPublishedAt());
        assertEquals(ProgrammingLanguage.JAVA, savedAssignment.getLanguage());
        assertEquals(100, savedAssignment.getMaxScore());
        assertEquals(dueAt, savedAssignment.getDueAt());
        assertEquals(90, savedAssignment.getTimeLimitMinutes());
        assertEquals("PENDING_SUBMISSION", savedAssignment.getReviewStatus());
        assertEquals(0, savedAssignment.getReviewedQuestionCount());

        verify(assignmentRepository).save(any(AssessmentAssignment.class));
    }

    @Test
    void assignAssessment_whenDueDateIsPast_throwsIllegalArgumentException() {
        Assessment assessment = assessment("assessment-1", "org-1");

        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");
        request.setDueAt(Instant.now().minusSeconds(60));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.assignAssessment(request)
        );

        assertEquals("Due date must be in the future.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void assignAssessment_whenTimeLimitExceedsMaximum_throwsIllegalArgumentException() {
        Assessment assessment = assessment("assessment-1", "org-1");

        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");
        request.setTimeLimitMinutes(481);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.assignAssessment(request)
        );

        assertEquals("Time limit cannot exceed 480 minutes.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void assignAssessment_whenTimeLimitIsNotPositive_throwsIllegalArgumentException() {
        Assessment assessment = assessment("assessment-1", "org-1");

        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");
        request.setTimeLimitMinutes(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.assignAssessment(request)
        );

        assertEquals("Time limit must be greater than zero.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void assignAssessment_whenAlreadyAssigned_throwsIllegalArgumentException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        Assessment assessment = assessment("assessment-1", "org-1");
        Candidate candidate = candidate("candidate-1", "org-1");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(organizationRepository.findById("org-1")).thenReturn(Optional.empty());
        when(assignmentRepository.existsByAssessmentIdAndCandidateId("assessment-1", "candidate-1"))
                .thenReturn(true);

        AssignAssessmentRequest request = assignRequest("assessment-1", "candidate-1");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.assignAssessment(request)
        );

        assertEquals("Assessment is already assigned to this candidate", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void getAllAssignments_returnsOnlyAssignmentsFromAdminOrganization() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        AssessmentAssignment assignmentOne = assignment("assignment-1", "candidate-1", "org-1");
        AssessmentAssignment assignmentTwo = assignment("assignment-2", "candidate-2", "org-1");
        assignmentOne.setDraftCode("in-progress code");
        assignmentOne.setDraftAnswers(Map.of("question-1", "in-progress answer"));
        assignmentOne.setDraftSavedAt(Instant.now());

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findByOrganizationId("org-1"))
                .thenReturn(List.of(assignmentOne, assignmentTwo));

        List<AssessmentAssignment> assignments = assessmentService.getAllAssignments();

        assertEquals(2, assignments.size());
        assertEquals("assignment-1", assignments.get(0).getId());
        assertEquals("assignment-2", assignments.get(1).getId());
        assertNull(assignments.get(0).getDraftCode());
        assertTrue(assignments.get(0).getDraftAnswers().isEmpty());
        assertNull(assignments.get(0).getDraftSavedAt());

        verify(assignmentRepository).findByOrganizationId("org-1");
    }

    @Test
    void createAssessment_whenMixedAssessmentHasCodingSubsetScore_acceptsCodingTestCaseTotal() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assessmentRepository.save(any(Assessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateAssessmentRequest request = new CreateAssessmentRequest();
        request.setTitle("Associate Software Developer - Java Array Skills");
        request.setDescription("Java arrays assessment");
        request.setRoleTitle("Associate Software Developer");
        request.setStatus(AssessmentStatus.PUBLISHED);
        request.setType(AssessmentType.CODING_CHALLENGE);
        request.setLanguage(ProgrammingLanguage.JAVA);
        request.setMaxScore(50);
        request.setPrompt("Find the second largest unique element.");
        request.setStarterCode("public class Main {}");
        request.setExpectedOutput("20");
        request.setSections(List.of(
                section(
                        "section-1",
                        "Array Fundamentals",
                        multipleChoiceQuestion("question-1", 25),
                        multipleChoiceQuestion("question-2", 25)
                ),
                section(
                        "section-2",
                        "Array Manipulation Challenge",
                        codingQuestion("question-3", 50)
                )
        ));

        Assessment savedAssessment = assessmentService.createAssessment(request);

        assertEquals(AssessmentType.CODING_CHALLENGE, savedAssessment.getType());
        assertEquals(1, savedAssessment.getVersion());
        assertNotNull(savedAssessment.getPublishedAt());
        assertEquals(100, savedAssessment.getMaxScore());
        assertEquals(50, savedAssessment.getTestCases().stream()
                .mapToInt(testCase -> testCase.getPoints() == null ? 0 : testCase.getPoints())
                .sum());
        assertEquals(50, savedAssessment.getSections().get(1).getQuestions().get(0).getPoints());

        verify(assessmentRepository).save(any(Assessment.class));
    }

    @Test
    void updateAssessment_whenAdminOwnsAssessment_incrementsVersionAndKeepsOrganization() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        Assessment assessment = assessment("assessment-1", "org-1");
        assessment.setVersion(2);
        assessment.setStatus(AssessmentStatus.DRAFT);

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assessmentRepository.findById("assessment-1")).thenReturn(Optional.of(assessment));
        when(assessmentRepository.save(any(Assessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateAssessmentRequest request = new CreateAssessmentRequest();
        request.setTitle("Updated Java Assessment");
        request.setDescription("Updated description");
        request.setRoleTitle("Backend Engineer");
        request.setStatus(AssessmentStatus.PUBLISHED);
        request.setType(AssessmentType.CODING_CHALLENGE);
        request.setLanguage(ProgrammingLanguage.JAVA);
        request.setMaxScore(100);
        request.setPrompt("Print Updated SkillSync");
        request.setStarterCode("public class Main {}");
        request.setExpectedOutput("Updated SkillSync");
        request.setSections(List.of(section(
                "section-1",
                "Coding",
                codingQuestion("coding-1", 50)
        )));

        Assessment savedAssessment = assessmentService.updateAssessment("assessment-1", request);

        assertEquals("Updated Java Assessment", savedAssessment.getTitle());
        assertEquals("org-1", savedAssessment.getOrganizationId());
        assertEquals(3, savedAssessment.getVersion());
        assertEquals(AssessmentStatus.PUBLISHED, savedAssessment.getStatus());
        assertNotNull(savedAssessment.getPublishedAt());
    }

    @Test
    void getAssignmentsForCurrentCandidate_usesCandidateProfilesLinkedToCurrentUser() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate orgOneProfile = candidate("candidate-profile-1", "org-1");
        Candidate orgTwoProfile = candidate("candidate-profile-2", "org-2");

        AssessmentAssignment assignmentOne = assignment("assignment-1", "candidate-profile-1", "org-1");
        AssessmentAssignment assignmentTwo = assignment("assignment-2", "candidate-profile-2", "org-2");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(candidateRepository.findAllByUserId("user-1"))
                .thenReturn(List.of(orgOneProfile, orgTwoProfile));
        when(assignmentRepository.findByCandidateIdIn(List.of("candidate-profile-1", "candidate-profile-2")))
                .thenReturn(List.of(assignmentOne, assignmentTwo));

        List<AssessmentAssignment> assignments = assessmentService.getAssignmentsForCurrentCandidate();

        assertEquals(2, assignments.size());
        assertEquals("assignment-1", assignments.get(0).getId());
        assertEquals("assignment-2", assignments.get(1).getId());

        verify(assignmentRepository).findByCandidateIdIn(List.of("candidate-profile-1", "candidate-profile-2"));
    }

    @Test
    void getAssignmentsForCurrentCandidate_masksHiddenExecutionDetailsAndSummaries() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setActualOutput("Hidden Case [FAILED]\nsecret actual output");
        assignment.setExecutionError("Hidden Case:\nsecret runtime error");
        assignment.setTestCaseResults(List.of(
                executionResult("Visible Case", false, "1", "2", "2", ""),
                executionResult("Hidden Case", true, "secret input", "secret expected", "secret actual", "secret error")
        ));

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.findByCandidateIdIn(List.of("candidate-profile-1")))
                .thenReturn(List.of(assignment));

        List<AssessmentAssignment> assignments = assessmentService.getAssignmentsForCurrentCandidate();

        AssessmentAssignment safeAssignment = assignments.get(0);
        assertNull(safeAssignment.getActualOutput());
        assertNull(safeAssignment.getExecutionError());
        assertEquals("1", safeAssignment.getTestCaseResults().get(0).getInput());
        assertNull(safeAssignment.getTestCaseResults().get(1).getInput());
        assertNull(safeAssignment.getTestCaseResults().get(1).getExpectedOutput());
        assertNull(safeAssignment.getTestCaseResults().get(1).getActualOutput());
        assertNull(safeAssignment.getTestCaseResults().get(1).getError());
    }

    @Test
    void startAssignment_whenTimedAssignment_setsStartedAndExpiresAt() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setTimeLimitMinutes(45);

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment startedAssignment = assessmentService.startAssignment("assignment-1");

        assertNotNull(startedAssignment.getStartedAt());
        assertNotNull(startedAssignment.getExpiresAt());
        assertEquals(45, startedAssignment.getTimeLimitMinutes());
    }

    @Test
    void startAssignment_whenDueDatePassed_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setDueAt(Instant.now().minusSeconds(60));

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.startAssignment("assignment-1")
        );

        assertEquals("Assignment due date has passed.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenTimedAssignmentNotStarted_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setTimeLimitMinutes(45);

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode("public class Main {}");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.submitAssignment("assignment-1", request)
        );

        assertEquals("Start the assessment before working on it.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenTimedAssignmentExpired_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setTimeLimitMinutes(45);
        assignment.setStartedAt(Instant.now().minusSeconds(4_000));
        assignment.setExpiresAt(Instant.now().minusSeconds(60));

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode("public class Main {}");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.submitAssignment("assignment-1", request)
        );

        assertEquals("Assessment time limit has expired.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenDueDatePassed_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setDueAt(Instant.now().minusSeconds(60));

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode("public class Main {}");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.submitAssignment("assignment-1", request)
        );

        assertEquals("Assignment due date has passed.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenAlreadySubmittedAndExpired_returnsExistingSubmittedAssignment() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.SUBMITTED);
        assignment.setTimeLimitMinutes(45);
        assignment.setStartedAt(Instant.now().minusSeconds(4_000));
        assignment.setExpiresAt(Instant.now().minusSeconds(60));

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode("public class Main {}");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        AssessmentAssignment result = assessmentService.submitAssignment("assignment-1", request);

        assertEquals("assignment-1", result.getId());
        assertEquals(AssignmentStatus.SUBMITTED, result.getStatus());
        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenCandidateDoesNotOwnAssignment_throwsRuntimeException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment otherCandidateAssignment = assignment("assignment-1", "candidate-profile-2", "org-1");

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode("public class Main {}");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(otherCandidateAssignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> assessmentService.submitAssignment("assignment-1", request)
        );

        assertEquals("You are not allowed to access this assignment.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenCandidateOwnsCodingAssignment_savesSubmittedCode() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode(" public class Main {} ");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.submitAssignment("assignment-1", request);

        assertEquals(AssignmentStatus.SUBMITTED, savedAssignment.getStatus());
        assertEquals("public class Main {}", savedAssignment.getSubmittedCode());
        assertNull(savedAssignment.getSubmittedAnswer());
        assertEquals("PENDING_EXECUTION", savedAssignment.getExecutionStatus());
        assertNotNull(savedAssignment.getSubmittedAt());
        assertFalse(Boolean.TRUE.equals(savedAssignment.getAutoSubmitted()));
        verify(mongoTemplate).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(AssessmentAssignment.class)
        );
        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void saveAssignmentDraft_whenCandidateOwnsAssignment_persistsDraft() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        SaveAssignmentDraftRequest request = new SaveAssignmentDraftRequest();
        request.setDraftCode("public class Main {}");
        request.setDraftAnswers(Map.of("short-1", "  Draft explanation  "));

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.saveAssignmentDraft("assignment-1", request);

        assertEquals("public class Main {}", savedAssignment.getDraftCode());
        assertEquals(Map.of("short-1", "Draft explanation"), savedAssignment.getDraftAnswers());
        assertNotNull(savedAssignment.getDraftSavedAt());
        assertEquals(AssignmentStatus.ASSIGNED, savedAssignment.getStatus());
    }

    @Test
    void saveAssignmentDraft_whenAnswerIsTooLarge_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        SaveAssignmentDraftRequest request = new SaveAssignmentDraftRequest();
        request.setDraftAnswers(Map.of("short-1", "x".repeat(10_001)));

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.saveAssignmentDraft("assignment-1", request)
        );

        assertEquals("Draft answer is too large.", exception.getMessage());
        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void recordIntegrityEvent_whenCandidateOwnsActiveAssignment_appendsNormalizedEvent() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        RecordIntegrityEventRequest request = new RecordIntegrityEventRequest();
        request.setType("window_blur");
        request.setDetail("Candidate left the assessment window.");
        request.setSectionId("section-1");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.recordIntegrityEvent("assignment-1", request);

        assertEquals(1, savedAssignment.getIntegrityEvents().size());
        AssessmentIntegrityEvent event = savedAssignment.getIntegrityEvents().get(0);
        assertEquals("WINDOW_BLUR", event.getType());
        assertEquals("Candidate left the assessment window.", event.getDetail());
        assertEquals("section-1", event.getSectionId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void recordIntegrityEvent_whenAssignmentAlreadySubmitted_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setStatus(AssignmentStatus.SUBMITTED);

        RecordIntegrityEventRequest request = new RecordIntegrityEventRequest();
        request.setType("COPY");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.recordIntegrityEvent("assignment-1", request)
        );

        assertEquals("Integrity events can only be recorded for active assignments.", exception.getMessage());
        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void submitAssignment_whenPayloadIsBlank_usesSavedDraftAndClearsIt() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setDraftCode("public class Main {}");
        assignment.setDraftAnswers(Map.of("short-1", "Draft explanation"));
        assignment.setDraftSavedAt(Instant.now());

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.submitAssignment("assignment-1", request);

        assertEquals(AssignmentStatus.SUBMITTED, savedAssignment.getStatus());
        assertEquals("public class Main {}", savedAssignment.getSubmittedCode());
        assertEquals(Map.of("short-1", "Draft explanation"), savedAssignment.getSubmittedAnswers());
        assertNull(savedAssignment.getDraftCode());
        assertTrue(savedAssignment.getDraftAnswers().isEmpty());
        assertNull(savedAssignment.getDraftSavedAt());
    }

    @Test
    void submitAssignment_whenAutoSubmittedWithinGrace_savesAutoSubmittedFlag() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setTimeLimitMinutes(45);
        assignment.setStartedAt(Instant.now().minusSeconds(2_800));
        assignment.setExpiresAt(Instant.now().minusSeconds(5));

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedCode("public class Main {}");
        request.setAutoSubmitted(true);

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.submitAssignment("assignment-1", request);

        assertEquals(AssignmentStatus.SUBMITTED, savedAssignment.getStatus());
        assertTrue(Boolean.TRUE.equals(savedAssignment.getAutoSubmitted()));
    }

    @Test
    void startAssignmentSection_whenCandidateOwnsAssignment_storesSectionAttempt() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setSections(List.of(section(
                "section-1",
                "Technical Knowledge",
                multipleChoiceQuestion("mcq-1", 10)
        )));

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.startAssignmentSection(
                "assignment-1",
                "section-1"
        );

        assertEquals(1, savedAssignment.getSectionAttempts().size());
        AssessmentSectionAttempt attempt = savedAssignment.getSectionAttempts().get(0);
        assertEquals("section-1", attempt.getSectionId());
        assertEquals("Technical Knowledge", attempt.getSectionTitle());
        assertNotNull(attempt.getStartedAt());
    }

    @Test
    void submitAssignment_whenStructuredSectionWasNotStarted_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.MCQ);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setSections(List.of(section(
                "section-1",
                "Technical Knowledge",
                multipleChoiceQuestion("mcq-1", 10)
        )));

        SubmitAssignmentRequest request = new SubmitAssignmentRequest();
        request.setSubmittedAnswers(Map.of("mcq-1", "mcq-1-option-1"));

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.submitAssignment("assignment-1", request)
        );

        assertEquals("Start every section before submitting the assessment.", exception.getMessage());
    }

    @Test
    void runAssignmentCode_whenAssignmentExpired_throwsIllegalArgumentException() {
        User candidateUser = candidateUser("user-1", "candidate@example.com");

        Candidate ownedCandidateProfile = candidate("candidate-profile-1", "org-1");
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-profile-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setTimeLimitMinutes(45);
        assignment.setStartedAt(Instant.now().minusSeconds(4_000));
        assignment.setExpiresAt(Instant.now().minusSeconds(60));

        RunCodeRequest request = new RunCodeRequest();
        request.setSourceCode("public class Main {}");

        setAuthenticatedUser(candidateUser.getEmail());

        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(candidateRepository.findAllByUserId("user-1")).thenReturn(List.of(ownedCandidateProfile));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.runAssignmentCode("assignment-1", request)
        );

        assertEquals("Assessment time limit has expired.", exception.getMessage());

        verify(codeExecutionService, never()).executeCode(any(), anyString(), anyString());
    }

    @Test
    void gradeAssignment_whenAdminDoesNotOwnAssignment_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        AssessmentAssignment assignment = assignment("assignment-1", "candidate-1", "org-2");
        assignment.setStatus(AssignmentStatus.SUBMITTED);

        GradeAssignmentRequest request = new GradeAssignmentRequest();
        request.setScore(80);
        request.setFeedback("Good work");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> assessmentService.gradeAssignment("assignment-1", request)
        );

        assertEquals("You are not allowed to access this assignment.", exception.getMessage());

        verify(assignmentRepository, never()).save(any(AssessmentAssignment.class));
    }

    @Test
    void gradeAssignment_whenAdminOwnsAssignment_savesScoreAndFeedback() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        AssessmentAssignment assignment = assignment("assignment-1", "candidate-1", "org-1");
        assignment.setStatus(AssignmentStatus.SUBMITTED);

        GradeAssignmentRequest request = new GradeAssignmentRequest();
        request.setScore(85);
        request.setFeedback("Strong submission");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.gradeAssignment("assignment-1", request);

        assertEquals(85, savedAssignment.getScore());
        assertEquals("Strong submission", savedAssignment.getFeedback());
        assertEquals(AssignmentStatus.GRADED, savedAssignment.getStatus());
        assertNotNull(savedAssignment.getGradedAt());
    }

    @Test
    void gradeAssignment_whenQuestionReviewsProvided_savesNormalizedReviews() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        AssessmentQuestion shortAnswer = shortAnswerQuestion("short-1", 20);
        AssessmentAssignment assignment = assignment("assignment-1", "candidate-1", "org-1");
        assignment.setStatus(AssignmentStatus.SUBMITTED);
        assignment.setSections(List.of(section("section-1", "Work style", shortAnswer)));

        QuestionReviewRequest review = new QuestionReviewRequest();
        review.setQuestionId("short-1");
        review.setQuestionTitle("Ignored title");
        review.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        review.setMaxPoints(100);
        review.setAwardedPoints(14);
        review.setNotes("Clear reasoning with one missing trade-off.");
        review.setReviewed(true);

        GradeAssignmentRequest request = new GradeAssignmentRequest();
        request.setScore(14);
        request.setFeedback("Useful practical judgment.");
        request.setQuestionReviews(List.of(review));

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAssignment savedAssignment = assessmentService.gradeAssignment("assignment-1", request);

        assertEquals(1, savedAssignment.getQuestionReviews().size());
        QuestionReview savedReview = savedAssignment.getQuestionReviews().get(0);
        assertEquals("short-1", savedReview.getQuestionId());
        assertEquals("Short short-1", savedReview.getQuestionTitle());
        assertEquals(QuestionType.SHORT_ANSWER, savedReview.getQuestionType());
        assertEquals(20, savedReview.getMaxPoints());
        assertEquals(14, savedReview.getAwardedPoints());
        assertEquals("Clear reasoning with one missing trade-off.", savedReview.getNotes());
        assertTrue(Boolean.TRUE.equals(savedReview.getReviewed()));
        assertEquals(14, savedAssignment.getManualReviewScore());
        assertEquals(0, savedAssignment.getAutoScore());
        assertEquals(1, savedAssignment.getReviewedQuestionCount());
        assertEquals(1, savedAssignment.getTotalQuestionCount());
        assertEquals("REVIEWED", savedAssignment.getReviewStatus());
    }

    @Test
    void executeAssignment_whenAdminOwnsSubmittedCodingAssignment_setsScoreBreakdown() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        AssessmentAssignment assignment = assignment("assignment-1", "candidate-1", "org-1");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setStatus(AssignmentStatus.SUBMITTED);
        assignment.setSubmittedCode("public class Main {}");
        assignment.setSubmittedAnswers(Map.of("mcq-1", "mcq-1-option-1"));
        assignment.setSections(List.of(section(
                "section-1",
                "Mixed skills",
                multipleChoiceQuestion("mcq-1", 10),
                codingQuestion("coding-1", 50)
        )));
        assignment.setTestCases(List.of(
                testCase("Visible", "1", "1", false, 20),
                testCase("Hidden", "2", "2", true, 30)
        ));

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(AssessmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(codeExecutionService.executeCode(any(), anyString(), eq("1"), eq("1")))
                .thenReturn(new CodeExecutionResult("JAVA", "1", "", 0, false, true));
        when(codeExecutionService.executeCode(any(), anyString(), eq("2"), eq("2")))
                .thenReturn(new CodeExecutionResult("JAVA", "2", "", 0, false, true));

        AssessmentAssignment savedAssignment = assessmentService.executeAssignment("assignment-1");

        assertEquals(AssignmentStatus.GRADED, savedAssignment.getStatus());
        assertEquals("PASSED", savedAssignment.getExecutionStatus());
        assertEquals(50, savedAssignment.getCodingScore());
        assertEquals(10, savedAssignment.getMultipleChoiceScore());
        assertEquals(60, savedAssignment.getAutoScore());
        assertEquals(60, savedAssignment.getScore());
        assertEquals("REVIEWED", savedAssignment.getReviewStatus());
    }

    @Test
    void executeAssignment_whenAdminDoesNotOwnAssignment_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        AssessmentAssignment assignment = assignment("assignment-1", "candidate-1", "org-2");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setSubmittedCode("public class Main {}");

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById("assignment-1")).thenReturn(Optional.of(assignment));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> assessmentService.executeAssignment("assignment-1")
        );

        assertEquals("You are not allowed to access this assignment.", exception.getMessage());

        verify(codeExecutionService, never()).executeCode(any(), anyString(), anyString());
    }

    private void setAuthenticatedUser(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null)
        );
    }

    private User adminUser(String id, String email, String organizationId) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(Role.ADMIN);
        user.setOrganizationId(organizationId);
        return user;
    }

    private User candidateUser(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(Role.CANDIDATE);
        return user;
    }

    private Assessment assessment(String id, String organizationId) {
        Assessment assessment = new Assessment();
        assessment.setId(id);
        assessment.setTitle("Java Hello World");
        assessment.setDescription("Print Hello SkillSync");
        assessment.setType(AssessmentType.CODING_CHALLENGE);
        assessment.setLanguage(ProgrammingLanguage.JAVA);
        assessment.setMaxScore(100);
        assessment.setPrompt("Print Hello SkillSync");
        assessment.setStarterCode("public class Main {}");
        assessment.setExpectedOutput("Hello SkillSync");
        assessment.setOrganizationId(organizationId);
        return assessment;
    }

    private Candidate candidate(String id, String organizationId) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setName("Candidate Demo");
        candidate.setEmail("candidate@example.com");
        candidate.setOrganizationId(organizationId);
        return candidate;
    }

    private AssessmentAssignment assignment(String id, String candidateId, String organizationId) {
        AssessmentAssignment assignment = new AssessmentAssignment();
        assignment.setId(id);
        assignment.setAssessmentId("assessment-1");
        assignment.setAssessmentTitle("Java Hello World");
        assignment.setAssessmentType(AssessmentType.CODING_CHALLENGE);
        assignment.setLanguage(ProgrammingLanguage.JAVA);
        assignment.setPrompt("Print Hello SkillSync");
        assignment.setExpectedOutput("Hello SkillSync");
        assignment.setCandidateId(candidateId);
        assignment.setCandidateName("Candidate Demo");
        assignment.setCandidateEmail("candidate@example.com");
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setExecutionStatus("NOT_RUN");
        assignment.setMaxScore(100);
        assignment.setOrganizationId(organizationId);
        return assignment;
    }

    private AssessmentSection section(
            String id,
            String title,
            AssessmentQuestion... questions
    ) {
        AssessmentSection section = new AssessmentSection();
        section.setId(id);
        section.setTitle(title);
        section.setQuestions(List.of(questions));
        return section;
    }

    private AssessmentQuestion multipleChoiceQuestion(String id, int points) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setType(QuestionType.MULTIPLE_CHOICE);
        question.setTitle("MCQ " + id);
        question.setPrompt("Choose the correct answer.");
        question.setPoints(points);
        question.setLanguage(ProgrammingLanguage.TEXT);

        AssessmentQuestionOption correct = new AssessmentQuestionOption();
        correct.setId(id + "-option-1");
        correct.setText("Correct");
        correct.setCorrect(true);

        AssessmentQuestionOption incorrect = new AssessmentQuestionOption();
        incorrect.setId(id + "-option-2");
        incorrect.setText("Incorrect");
        incorrect.setCorrect(false);

        question.setOptions(List.of(correct, incorrect));
        return question;
    }

    private AssessmentQuestion shortAnswerQuestion(String id, int points) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setType(QuestionType.SHORT_ANSWER);
        question.setTitle("Short " + id);
        question.setPrompt("Explain your reasoning.");
        question.setPoints(points);
        question.setLanguage(ProgrammingLanguage.TEXT);
        question.setCorrectAnswer("Look for concise reasoning and practical trade-offs.");
        return question;
    }

    private AssessmentQuestion codingQuestion(String id, int points) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setType(QuestionType.CODING_CHALLENGE);
        question.setTitle("Coding " + id);
        question.setPrompt("Find the second largest unique element.");
        question.setPoints(points);
        question.setLanguage(ProgrammingLanguage.JAVA);
        question.setStarterCode("public class Main {}");
        question.setExpectedOutput("20");
        question.setTestCases(List.of(
                testCase("Basic Case", "10 20 5 30 15", "20", false, 10),
                testCase("Duplicate Elements", "5 5 8 8 10 10 3", "8", true, 10),
                testCase("Few Unique Elements", "7 7 7 4 4", "4", true, 10),
                testCase("Single Unique", "100 100 100", "-1", true, 10),
                testCase("One Number", "5", "-1", true, 10)
        ));
        return question;
    }

    private AssessmentTestCase testCase(
            String name,
            String input,
            String expectedOutput,
            boolean hidden,
            int points
    ) {
        AssessmentTestCase testCase = new AssessmentTestCase();
        testCase.setName(name);
        testCase.setInput(input);
        testCase.setExpectedOutput(expectedOutput);
        testCase.setHidden(hidden);
        testCase.setPoints(points);
        return testCase;
    }

    private TestCaseExecutionResult executionResult(
            String name,
            boolean hidden,
            String input,
            String expectedOutput,
            String actualOutput,
            String error
    ) {
        TestCaseExecutionResult result = new TestCaseExecutionResult();
        result.setName(name);
        result.setHidden(hidden);
        result.setInput(input);
        result.setExpectedOutput(expectedOutput);
        result.setActualOutput(actualOutput);
        result.setError(error);
        result.setPassed(error == null || error.isBlank());
        result.setPoints(10);
        result.setAwardedPoints(result.getPassed() ? 10 : 0);
        return result;
    }

    private AssignAssessmentRequest assignRequest(String assessmentId, String candidateId) {
        AssignAssessmentRequest request = new AssignAssessmentRequest();
        request.setAssessmentId(assessmentId);
        request.setCandidateId(candidateId);
        return request;
    }
}
