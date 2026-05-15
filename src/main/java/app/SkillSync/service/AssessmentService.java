package app.SkillSync.service;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.model.*;
import app.SkillSync.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.dto.RunCodeRequest;

import java.time.Instant;
import java.util.List;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentAssignmentRepository assignmentRepository;
    private final CandidateRepository candidateRepository;
    private final CodeExecutionService codeExecutionService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            AssessmentAssignmentRepository assignmentRepository,
            CandidateRepository candidateRepository,
            CodeExecutionService codeExecutionService,
            UserRepository userRepository,
            OrganizationRepository organizationRepository
    ) {
        this.assessmentRepository = assessmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.candidateRepository = candidateRepository;
        this.codeExecutionService = codeExecutionService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    public Assessment createAssessment(CreateAssessmentRequest request) {
        Assessment assessment = new Assessment();
        assessment.setTitle(request.getTitle().trim());
        assessment.setDescription(request.getDescription());
        assessment.setType(request.getType());
        assessment.setLanguage(resolveLanguage(request));
        assessment.setMaxScore(request.getMaxScore());
        assessment.setPrompt(request.getPrompt().trim());
        assessment.setStarterCode(request.getStarterCode());
        assessment.setExpectedOutput(request.getExpectedOutput());
        assessment.setCreatedAt(Instant.now());

        User adminUser = getCurrentUser();

        if (adminUser.getOrganizationId() == null || adminUser.getOrganizationId().isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        assessment.setOrganizationId(adminUser.getOrganizationId());

        return assessmentRepository.save(assessment);
    }

    public List<Assessment> getAllAssessments() {
        User adminUser = getCurrentUser();

        if (adminUser.getOrganizationId() == null || adminUser.getOrganizationId().isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        return assessmentRepository.findByOrganizationId(adminUser.getOrganizationId());
    }

    public Assessment getAssessmentById(String assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
    }

    public AssessmentAssignment assignAssessment(AssignAssessmentRequest request) {
        Assessment assessment = getAssessmentById(request.getAssessmentId());

        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        User adminUser = getCurrentUser();
        String organizationId = adminUser.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        String organizationName = organizationRepository.findById(organizationId)
                .map(Organization::getName)
                .orElse("Organization");

        if (!organizationId.equals(candidate.getOrganizationId())) {
            throw new RuntimeException("Candidate does not belong to your organization.");
        }

        if (!organizationId.equals(assessment.getOrganizationId())) {
            throw new RuntimeException("Assessment does not belong to your organization.");
        }

        boolean alreadyAssigned = assignmentRepository.existsByAssessmentIdAndCandidateId(
                assessment.getId(),
                candidate.getId()
        );

        if (alreadyAssigned) {
            throw new IllegalArgumentException("Assessment is already assigned to this candidate");
        }

        AssessmentAssignment assignment = new AssessmentAssignment();
        assignment.setAssessmentId(assessment.getId());
        assignment.setAssessmentTitle(assessment.getTitle());
        assignment.setAssessmentType(assessment.getType());
        assignment.setLanguage(assessment.getLanguage());
        assignment.setPrompt(assessment.getPrompt());
        assignment.setStarterCode(assessment.getStarterCode());
        assignment.setExpectedOutput(assessment.getExpectedOutput());
        assignment.setMaxScore(assessment.getMaxScore());

        assignment.setCandidateId(candidate.getId());
        assignment.setCandidateName(candidate.getName());
        assignment.setCandidateEmail(candidate.getEmail());

        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setExecutionStatus("NOT_RUN");
        assignment.setAssignedAt(Instant.now());
        assignment.setOrganizationId(organizationId);
        assignment.setOrganizationName(organizationName);

        return assignmentRepository.save(assignment);
    }

    public List<AssessmentAssignment> getAllAssignments() {
        User adminUser = getCurrentUser();
        String organizationId = adminUser.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        return assignmentRepository.findByOrganizationId(organizationId);
    }

    public List<AssessmentAssignment> getAssignmentsForCandidateEmail(String candidateEmail) {
        return assignmentRepository.findByCandidateEmailIgnoreCase(candidateEmail);
    }

    public List<AssessmentAssignment> getAssignmentsForCurrentCandidate() {
        User candidateUser = getCurrentUser();

        List<Candidate> candidateProfiles =
                candidateRepository.findAllByUserId(candidateUser.getId());

        List<String> candidateIds = candidateProfiles.stream()
                .map(Candidate::getId)
                .toList();

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        return assignmentRepository.findByCandidateIdIn(candidateIds)
                .stream()
                .map(this::hideSensitiveCandidateFields)
                .toList();
    }

    public List<AssessmentAssignment> getAssignmentsForCandidateId(String candidateId) {
        return assignmentRepository.findByCandidateId(candidateId);
    }

    public AssessmentAssignment submitAssignment(
            String assignmentId,
            SubmitAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);

        if (assignment.getStatus() == AssignmentStatus.SUBMITTED || assignment.getStatus() == AssignmentStatus.GRADED) {
            throw new IllegalArgumentException("Assignment has already been submitted");
        }

        boolean isCodingChallenge = assignment.getAssessmentType() == AssessmentType.CODING_CHALLENGE;

        if (isCodingChallenge) {
            if (request.getSubmittedCode() == null || request.getSubmittedCode().trim().isEmpty()) {
                throw new IllegalArgumentException("Submitted code is required");
            }

            assignment.setSubmittedCode(request.getSubmittedCode().trim());
            assignment.setSubmittedAnswer(null);
            assignment.setExecutionStatus("PENDING_EXECUTION");
        } else {
            if (request.getSubmittedAnswer() == null || request.getSubmittedAnswer().trim().isEmpty()) {
                throw new IllegalArgumentException("Submitted answer is required");
            }

            assignment.setSubmittedAnswer(request.getSubmittedAnswer().trim());
            assignment.setSubmittedCode(null);
            assignment.setExecutionStatus("NOT_APPLICABLE");
        }

        assignment.setStatus(AssignmentStatus.SUBMITTED);
        assignment.setSubmittedAt(Instant.now());

        AssessmentAssignment savedAssignment = assignmentRepository.save(assignment);
        return hideSensitiveCandidateFields(savedAssignment);
    }

    private void validateCandidateCanAccessAssignment(AssessmentAssignment assignment) {
        User candidateUser = getCurrentUser();

        List<Candidate> candidateProfiles =
                candidateRepository.findAllByUserId(candidateUser.getId());

        boolean ownsAssignment = candidateProfiles.stream()
                .anyMatch(candidate -> candidate.getId().equals(assignment.getCandidateId()));

        if (!ownsAssignment) {
            throw new RuntimeException("You are not allowed to access this assignment.");
        }
    }

    public AssessmentAssignment gradeAssignment(
            String assignmentId,
            GradeAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found."));

        validateAdminCanAccessAssignment(assignment);

        if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Assignment has not been submitted yet");
        }

        assignment.setScore(request.getScore());
        assignment.setFeedback(request.getFeedback());
        assignment.setStatus(AssignmentStatus.GRADED);
        assignment.setGradedAt(Instant.now());

        return assignmentRepository.save(assignment);
    }

    private ProgrammingLanguage resolveLanguage(CreateAssessmentRequest request) {
        if (request.getType() == AssessmentType.QUIZ) {
            return ProgrammingLanguage.TEXT;
        }

        if (request.getLanguage() == null || request.getLanguage() == ProgrammingLanguage.TEXT) {
            return ProgrammingLanguage.JAVA;
        }

        return request.getLanguage();
    }
    public AssessmentAssignment executeAssignment(String assignmentId) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found."));

        validateAdminCanAccessAssignment(assignment);

        if (assignment.getAssessmentType() != AssessmentType.CODING_CHALLENGE) {
            throw new IllegalArgumentException("Only coding challenges can be executed");
        }

        if (assignment.getSubmittedCode() == null || assignment.getSubmittedCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Assignment does not have submitted code");
        }

        assignment.setExecutionStatus("RUNNING");
        assignmentRepository.save(assignment);

        var executionResult = codeExecutionService.executeCode(
                assignment.getLanguage(),
                assignment.getSubmittedCode(),
                assignment.getExpectedOutput()
        );

        assignment.setActualOutput(executionResult.getStdout());
        assignment.setExecutionError(executionResult.getStderr());

        if (executionResult.isTimedOut()) {
            assignment.setExecutionStatus("TIMEOUT");
            assignment.setScore(0);
            assignment.setFeedback("Code execution timed out.");
            assignment.setStatus(AssignmentStatus.GRADED);
            assignment.setGradedAt(Instant.now());
            return assignmentRepository.save(assignment);
        }

        if (executionResult.getExitCode() == null || executionResult.getExitCode() != 0) {
            assignment.setExecutionStatus("ERROR");
            assignment.setScore(0);
            assignment.setFeedback("Code execution failed. Check execution error.");
            assignment.setStatus(AssignmentStatus.GRADED);
            assignment.setGradedAt(Instant.now());
            return assignmentRepository.save(assignment);
        }

        if (executionResult.isOutputMatched()) {
            assignment.setExecutionStatus("PASSED");
            assignment.setScore(assignment.getMaxScore() != null ? assignment.getMaxScore() : 100);
            assignment.setFeedback("Automatic grading passed. Output matched expected output.");
        } else {
            assignment.setExecutionStatus("FAILED");
            assignment.setScore(0);
            assignment.setFeedback("Automatic grading failed. Output did not match expected output.");
        }

        assignment.setStatus(AssignmentStatus.GRADED);
        assignment.setGradedAt(Instant.now());

        return assignmentRepository.save(assignment);
    }
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found."));
    }
    private void validateAdminCanAccessAssignment(AssessmentAssignment assignment) {
        User adminUser = getCurrentUser();

        if (adminUser.getOrganizationId() == null || adminUser.getOrganizationId().isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        if (assignment.getOrganizationId() == null ||
                !assignment.getOrganizationId().equals(adminUser.getOrganizationId())) {
            throw new RuntimeException("You are not allowed to access this assignment.");
        }
    }

    public CodeExecutionResult runAssignmentCode(
            String assignmentId,
            RunCodeRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);

        if (assignment.getAssessmentType() != AssessmentType.CODING_CHALLENGE) {
            throw new IllegalArgumentException("Only coding challenges can be executed");
        }

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only assigned assessments can be run before submission");
        }

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Source code is required");
        }

        return codeExecutionService.executeCode(
                assignment.getLanguage(),
                request.getSourceCode().trim(),
                null
        );
    }

    private AssessmentAssignment hideSensitiveCandidateFields(AssessmentAssignment assignment) {
        assignment.setExpectedOutput(null);
        return assignment;
    }

}