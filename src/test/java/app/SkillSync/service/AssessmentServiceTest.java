package app.SkillSync.service;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentServiceTest {

    private AssessmentRepository assessmentRepository;
    private AssessmentAssignmentRepository assignmentRepository;
    private CandidateRepository candidateRepository;
    private CodeExecutionService codeExecutionService;
    private UserRepository userRepository;
    private OrganizationRepository organizationRepository;
    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentRepository = mock(AssessmentRepository.class);
        assignmentRepository = mock(AssessmentAssignmentRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        codeExecutionService = mock(CodeExecutionService.class);
        userRepository = mock(UserRepository.class);
        organizationRepository = mock(OrganizationRepository.class);

        assessmentService = new AssessmentService(
                assessmentRepository,
                assignmentRepository,
                candidateRepository,
                codeExecutionService,
                userRepository,
                organizationRepository
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

        AssessmentAssignment savedAssignment = assessmentService.assignAssessment(request);

        assertEquals("assessment-1", savedAssignment.getAssessmentId());
        assertEquals("candidate-1", savedAssignment.getCandidateId());
        assertEquals("org-1", savedAssignment.getOrganizationId());
        assertEquals("SkillSync Demo Org", savedAssignment.getOrganizationName());
        assertEquals(AssignmentStatus.ASSIGNED, savedAssignment.getStatus());
        assertEquals("NOT_RUN", savedAssignment.getExecutionStatus());
        assertEquals(AssessmentType.CODING_CHALLENGE, savedAssignment.getAssessmentType());
        assertEquals(ProgrammingLanguage.JAVA, savedAssignment.getLanguage());
        assertEquals(100, savedAssignment.getMaxScore());

        verify(assignmentRepository).save(any(AssessmentAssignment.class));
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

        setAuthenticatedUser(admin.getEmail());

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assignmentRepository.findByOrganizationId("org-1"))
                .thenReturn(List.of(assignmentOne, assignmentTwo));

        List<AssessmentAssignment> assignments = assessmentService.getAllAssignments();

        assertEquals(2, assignments.size());
        assertEquals("assignment-1", assignments.get(0).getId());
        assertEquals("assignment-2", assignments.get(1).getId());

        verify(assignmentRepository).findByOrganizationId("org-1");
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

    private AssignAssessmentRequest assignRequest(String assessmentId, String candidateId) {
        AssignAssessmentRequest request = new AssignAssessmentRequest();
        request.setAssessmentId(assessmentId);
        request.setCandidateId(candidateId);
        return request;
    }
}