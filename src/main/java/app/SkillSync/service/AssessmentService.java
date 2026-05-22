package app.SkillSync.service;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.AssignmentRunResult;
import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.dto.QuestionReviewRequest;
import app.SkillSync.dto.RunCodeRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.dto.TestCaseRunSummary;
import app.SkillSync.model.Assessment;
import app.SkillSync.model.AssessmentAssignment;
import app.SkillSync.model.AssessmentQuestion;
import app.SkillSync.model.AssessmentQuestionOption;
import app.SkillSync.model.AssessmentSection;
import app.SkillSync.model.AssessmentSectionAttempt;
import app.SkillSync.model.AssessmentStatus;
import app.SkillSync.model.AssessmentTestCase;
import app.SkillSync.model.AssessmentType;
import app.SkillSync.model.AssignmentStatus;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Organization;
import app.SkillSync.model.ProgrammingLanguage;
import app.SkillSync.model.QuestionReview;
import app.SkillSync.model.QuestionType;
import app.SkillSync.model.TestCaseExecutionResult;
import app.SkillSync.model.User;
import app.SkillSync.repository.AssessmentAssignmentRepository;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssessmentService {

    private static final long AUTO_SUBMIT_GRACE_SECONDS = 30;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentAssignmentRepository assignmentRepository;
    private final CandidateRepository candidateRepository;
    private final CodeExecutionService codeExecutionService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final BillingService billingService;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            AssessmentAssignmentRepository assignmentRepository,
            CandidateRepository candidateRepository,
            CodeExecutionService codeExecutionService,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            BillingService billingService
    ) {
        this.assessmentRepository = assessmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.candidateRepository = candidateRepository;
        this.codeExecutionService = codeExecutionService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.billingService = billingService;
    }

    public Assessment createAssessment(CreateAssessmentRequest request) {
        User adminUser = getCurrentUser();

        if (adminUser.getOrganizationId() == null || adminUser.getOrganizationId().isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        billingService.ensureCanCreateAssessment(adminUser.getOrganizationId());

        ProgrammingLanguage resolvedLanguage = resolveLanguage(request);
        List<AssessmentSection> normalizedSections = normalizeAndValidateSections(request);
        AssessmentQuestion primaryQuestion = findPrimaryQuestion(normalizedSections);
        int executableMaxScore = primaryQuestion != null && primaryQuestion.getType() == QuestionType.CODING_CHALLENGE
                ? safeInt(primaryQuestion.getPoints())
                : request.getMaxScore();
        List<AssessmentTestCase> normalizedTestCases = normalizeAndValidateTestCases(
                resolveAssessmentType(request, primaryQuestion),
                executableMaxScore,
                primaryQuestion != null ? primaryQuestion.getExpectedOutput() : request.getExpectedOutput(),
                primaryQuestion != null && primaryQuestion.getTestCases() != null && !primaryQuestion.getTestCases().isEmpty()
                        ? primaryQuestion.getTestCases()
                        : request.getTestCases()
        );

        Assessment assessment = new Assessment();
        assessment.setTitle(request.getTitle().trim());
        assessment.setDescription(request.getDescription());
        assessment.setRoleTitle(request.getRoleTitle());
        assessment.setStatus(request.getStatus() == null ? AssessmentStatus.PUBLISHED : request.getStatus());
        assessment.setType(resolveAssessmentType(request, primaryQuestion));
        assessment.setLanguage(primaryQuestion != null && primaryQuestion.getLanguage() != null
                ? primaryQuestion.getLanguage()
                : resolvedLanguage);
        assessment.setMaxScore(resolveMaxScore(request, normalizedSections));
        assessment.setDurationMinutes(request.getDurationMinutes());
        assessment.setPrompt(primaryQuestion != null ? primaryQuestion.getPrompt() : request.getPrompt().trim());
        assessment.setStarterCode(primaryQuestion != null ? primaryQuestion.getStarterCode() : request.getStarterCode());
        assessment.setExpectedOutput(primaryQuestion != null ? primaryQuestion.getExpectedOutput() : request.getExpectedOutput());
        assessment.setTestCases(normalizedTestCases);
        assessment.setSections(normalizedSections);
        assessment.setCreatedAt(Instant.now());
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
        validateAssignmentSchedule(request);

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

        if (assessment.getStatus() == AssessmentStatus.DRAFT || assessment.getStatus() == AssessmentStatus.ARCHIVED) {
            throw new IllegalArgumentException("Only published assessments can be assigned.");
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
        assignment.setAssessmentStatus(assessment.getStatus());
        assignment.setLanguage(assessment.getLanguage());
        assignment.setPrompt(assessment.getPrompt());
        assignment.setStarterCode(assessment.getStarterCode());
        assignment.setExpectedOutput(assessment.getExpectedOutput());
        assignment.setTestCases(copyTestCases(assessment.getTestCases()));
        assignment.setSections(copySections(assessment.getSections(), false));
        assignment.setMaxScore(assessment.getMaxScore());

        assignment.setCandidateId(candidate.getId());
        assignment.setCandidateName(candidate.getName());
        assignment.setCandidateEmail(candidate.getEmail());

        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAutoSubmitted(false);
        assignment.setExecutionStatus("NOT_RUN");
        assignment.setAssignedAt(Instant.now());
        assignment.setDueAt(request.getDueAt());
        assignment.setTimeLimitMinutes(request.getTimeLimitMinutes());
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
                .map(this::toCandidateSafeAssignment)
                .toList();
    }

    public List<AssessmentAssignment> getAssignmentsForCandidateId(String candidateId) {
        return assignmentRepository.findByCandidateId(candidateId);
    }

    public AssessmentAssignment startAssignment(String assignmentId) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only assigned assessments can be started");
        }

        validateAssignmentDueDateOpen(assignment);

        if (assignment.getStartedAt() == null) {
            Instant now = Instant.now();
            assignment.setStartedAt(now);

            if (assignment.getTimeLimitMinutes() != null) {
                assignment.setExpiresAt(now.plus(assignment.getTimeLimitMinutes(), ChronoUnit.MINUTES));
            }
        }

        return toCandidateSafeAssignment(assignmentRepository.save(assignment));
    }

    public AssessmentAssignment startAssignmentSection(String assignmentId, String sectionId) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);
        validateAssignmentCanBeWorkedOn(assignment);

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only assigned assessments can be worked on");
        }

        AssessmentSection section = findAssignmentSection(assignment, sectionId);
        AssessmentSectionAttempt attempt = findSectionAttempt(assignment, section.getId());

        if (attempt == null) {
            Instant now = Instant.now();
            attempt = new AssessmentSectionAttempt();
            attempt.setSectionId(section.getId());
            attempt.setSectionTitle(section.getTitle());
            attempt.setTimeLimitMinutes(section.getTimeLimitMinutes());
            attempt.setStartedAt(now);

            if (section.getTimeLimitMinutes() != null) {
                attempt.setExpiresAt(now.plus(section.getTimeLimitMinutes(), ChronoUnit.MINUTES));
            }

            assignment.getSectionAttempts().add(attempt);
        }

        validateSectionAttemptOpen(attempt, false);

        return toCandidateSafeAssignment(assignmentRepository.save(assignment));
    }

    public AssessmentAssignment completeAssignmentSection(String assignmentId, String sectionId) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);
        validateAssignmentCanBeWorkedOn(assignment);

        AssessmentSection section = findAssignmentSection(assignment, sectionId);
        AssessmentSectionAttempt attempt = findSectionAttempt(assignment, section.getId());

        if (attempt == null) {
            throw new IllegalArgumentException("Start the section before completing it.");
        }

        validateSectionAttemptOpen(attempt, false);

        if (attempt.getCompletedAt() == null) {
            attempt.setCompletedAt(Instant.now());
        }

        return toCandidateSafeAssignment(assignmentRepository.save(assignment));
    }

    public AssessmentAssignment submitAssignment(
            String assignmentId,
            SubmitAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);
        boolean autoSubmitted = Boolean.TRUE.equals(request.getAutoSubmitted());
        validateAssignmentCanBeWorkedOn(assignment, autoSubmitted);
        validateSubmittedSections(assignment, autoSubmitted);

        if (assignment.getStatus() == AssignmentStatus.SUBMITTED || assignment.getStatus() == AssignmentStatus.GRADED) {
            throw new IllegalArgumentException("Assignment has already been submitted");
        }

        boolean isCodingChallenge = assignment.getAssessmentType() == AssessmentType.CODING_CHALLENGE;

        if (isCodingChallenge) {
            if (request.getSubmittedCode() == null || request.getSubmittedCode().trim().isEmpty()) {
                throw new IllegalArgumentException("Submitted code is required");
            }

            assignment.setSubmittedCode(request.getSubmittedCode().trim());
            assignment.setSubmittedAnswers(normalizeSubmittedAnswers(request));
            assignment.setSubmittedAnswer(null);
            assignment.setExecutionStatus("PENDING_EXECUTION");
            assignment.setTestCaseResults(new ArrayList<>());
            assignment.setActualOutput(null);
            assignment.setExecutionError(null);
        } else {
            Map<String, String> submittedAnswers = normalizeSubmittedAnswers(request);

            if (submittedAnswers.isEmpty()) {
                throw new IllegalArgumentException("Submitted answer is required");
            }

            assignment.setSubmittedAnswers(submittedAnswers);
            assignment.setSubmittedAnswer(resolveSubmittedAnswerSummary(submittedAnswers));
            assignment.setSubmittedCode(null);
            assignment.setExecutionStatus("NOT_APPLICABLE");
            applyAutoScoreForObjectiveQuestions(assignment, submittedAnswers);
        }

        assignment.setStatus(AssignmentStatus.SUBMITTED);
        assignment.setAutoSubmitted(autoSubmitted);
        Instant submittedAt = Instant.now();
        assignment.setSubmittedAt(submittedAt);
        assignment.setCompletedAt(submittedAt);
        markOpenSectionAttemptsCompleted(assignment, submittedAt);

        AssessmentAssignment savedAssignment = assignmentRepository.save(assignment);
        return toCandidateSafeAssignment(savedAssignment);
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

        int maxScore = safeInt(assignment.getMaxScore());
        if (maxScore > 0 && request.getScore() > maxScore) {
            throw new IllegalArgumentException("Score cannot exceed the assignment max score.");
        }

        assignment.setScore(request.getScore());
        assignment.setFeedback(request.getFeedback());
        assignment.setQuestionReviews(normalizeQuestionReviews(assignment, request.getQuestionReviews()));
        assignment.setStatus(AssignmentStatus.GRADED);
        assignment.setGradedAt(Instant.now());

        return assignmentRepository.save(assignment);
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

        List<AssessmentTestCase> testCases = getExecutableTestCasesForAssignment(assignment);

        assignment.setExecutionStatus("RUNNING");
        assignmentRepository.save(assignment);

        List<TestCaseExecutionResult> testCaseResults = runTestCases(
                assignment.getLanguage(),
                assignment.getSubmittedCode(),
                testCases,
                false
        );

        int codingAwardedPoints = testCaseResults.stream()
                .mapToInt(result -> safeInt(result.getAwardedPoints()))
                .sum();

        int totalPoints = testCaseResults.stream()
                .mapToInt(result -> safeInt(result.getPoints()))
                .sum();

        int objectiveAwardedPoints = calculateObjectiveScore(
                assignment,
                assignment.getSubmittedAnswers()
        );

        int awardedPoints = codingAwardedPoints + objectiveAwardedPoints;

        long passedTests = testCaseResults.stream()
                .filter(result -> Boolean.TRUE.equals(result.getPassed()))
                .count();

        assignment.setTestCaseResults(testCaseResults);
        assignment.setActualOutput(buildActualOutputSummary(testCaseResults));
        assignment.setExecutionError(buildExecutionErrorSummary(testCaseResults));
        assignment.setScore(awardedPoints);
        assignment.setStatus(AssignmentStatus.GRADED);
        assignment.setGradedAt(Instant.now());

        if (testCaseResults.isEmpty()) {
            assignment.setExecutionStatus("ERROR");
            assignment.setFeedback("No executable test cases were available.");
            assignment.setScore(0);
            return assignmentRepository.save(assignment);
        }

        boolean anyTimeout = testCaseResults.stream()
                .anyMatch(result -> Boolean.TRUE.equals(result.getTimedOut()));

        boolean anyRuntimeError = testCaseResults.stream()
                .anyMatch(result ->
                        result.getExitCode() == null ||
                                result.getExitCode() != 0 ||
                                (result.getError() != null && !result.getError().isBlank())
                );

        if (passedTests == testCaseResults.size()) {
            assignment.setExecutionStatus("PASSED");
            assignment.setFeedback("Automatic grading passed. All test cases passed.");
        } else if (anyTimeout) {
            assignment.setExecutionStatus("TIMEOUT");
            assignment.setFeedback(
                    "Automatic grading completed with timeout. Passed "
                            + passedTests + "/" + testCaseResults.size()
                            + " test cases. Score: " + awardedPoints + "/" + assignment.getMaxScore() + "."
            );
        } else if (anyRuntimeError) {
            assignment.setExecutionStatus("ERROR");
            assignment.setFeedback(
                    "Automatic grading completed with execution errors. Passed "
                            + passedTests + "/" + testCaseResults.size()
                            + " test cases. Score: " + awardedPoints + "/" + assignment.getMaxScore() + "."
            );
        } else {
            assignment.setExecutionStatus("FAILED");
            assignment.setFeedback(
                    "Automatic grading completed. Passed "
                            + passedTests + "/" + testCaseResults.size()
                            + " test cases. Score: " + awardedPoints + "/" + assignment.getMaxScore() + "."
            );
        }

        return assignmentRepository.save(assignment);
    }

    public AssignmentRunResult runAssignmentCode(
            String assignmentId,
            RunCodeRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        validateCandidateCanAccessAssignment(assignment);
        validateAssignmentCanBeWorkedOn(assignment);

        if (assignment.getAssessmentType() != AssessmentType.CODING_CHALLENGE) {
            throw new IllegalArgumentException("Only coding challenges can be executed");
        }

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only assigned assessments can be run before submission");
        }

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Source code is required");
        }

        validateCodingSectionOpen(assignment);

        List<AssessmentTestCase> visibleTestCases = getExecutableTestCasesForAssignment(assignment)
                .stream()
                .filter(testCase -> !testCase.isHidden())
                .toList();

        if (visibleTestCases.isEmpty()) {
            throw new IllegalArgumentException("No visible sample test cases are available for this assignment.");
        }

        List<TestCaseExecutionResult> results = runTestCases(
                assignment.getLanguage(),
                request.getSourceCode().trim(),
                visibleTestCases,
                true
        );

        return toAssignmentRunResult(assignment.getLanguage(), results, true);
    }

    private List<TestCaseExecutionResult> runTestCases(
            ProgrammingLanguage language,
            String sourceCode,
            List<AssessmentTestCase> testCases,
            boolean candidateView
    ) {
        List<TestCaseExecutionResult> results = new ArrayList<>();

        for (AssessmentTestCase testCase : testCases) {
            CodeExecutionResult executionResult = codeExecutionService.executeCode(
                    language,
                    sourceCode,
                    safeString(testCase.getInput()),
                    testCase.getExpectedOutput()
            );

            TestCaseExecutionResult result = new TestCaseExecutionResult();
            result.setName(defaultName(testCase.getName(), results.size() + 1));
            result.setInput(testCase.getInput());
            result.setExpectedOutput(testCase.getExpectedOutput());
            result.setActualOutput(executionResult.getStdout());
            result.setError(executionResult.getStderr());
            result.setExitCode(executionResult.getExitCode());
            result.setTimedOut(executionResult.isTimedOut());
            result.setPassed(executionResult.isOutputMatched());
            result.setHidden(testCase.isHidden());
            result.setPoints(safeInt(testCase.getPoints()));
            result.setAwardedPoints(executionResult.isOutputMatched() ? safeInt(testCase.getPoints()) : 0);

            if (candidateView && Boolean.TRUE.equals(result.getHidden())) {
                maskHiddenResult(result);
            }

            results.add(result);
        }

        return results;
    }

    private AssignmentRunResult toAssignmentRunResult(
            ProgrammingLanguage language,
            List<TestCaseExecutionResult> results,
            boolean candidateView
    ) {
        AssignmentRunResult runResult = new AssignmentRunResult();

        runResult.setLanguage(language == null ? null : language.name());
        runResult.setTotalTests(results.size());
        runResult.setPassedTests((int) results.stream()
                .filter(result -> Boolean.TRUE.equals(result.getPassed()))
                .count());
        runResult.setTotalPoints(results.stream()
                .mapToInt(result -> safeInt(result.getPoints()))
                .sum());
        runResult.setAwardedPoints(results.stream()
                .mapToInt(result -> safeInt(result.getAwardedPoints()))
                .sum());

        List<TestCaseRunSummary> summaries = results.stream()
                .map(result -> toTestCaseRunSummary(result, candidateView))
                .toList();

        runResult.setTestResults(summaries);

        return runResult;
    }

    private TestCaseRunSummary toTestCaseRunSummary(
            TestCaseExecutionResult result,
            boolean candidateView
    ) {
        TestCaseRunSummary summary = new TestCaseRunSummary();

        summary.setName(result.getName());
        summary.setHidden(result.getHidden());
        summary.setPassed(result.getPassed());
        summary.setTimedOut(result.getTimedOut());
        summary.setExitCode(result.getExitCode());
        summary.setPoints(result.getPoints());
        summary.setAwardedPoints(result.getAwardedPoints());

        boolean hideDetails = candidateView && Boolean.TRUE.equals(result.getHidden());

        if (!hideDetails) {
            summary.setInput(result.getInput());
            summary.setExpectedOutput(result.getExpectedOutput());
            summary.setActualOutput(result.getActualOutput());
            summary.setError(result.getError());
        }

        return summary;
    }

    private AssessmentAssignment toCandidateSafeAssignment(AssessmentAssignment assignment) {
        AssessmentAssignment safe = copyAssignment(assignment);

        safe.setExpectedOutput(null);
        safe.setTestCases(maskHiddenTestCases(safe.getTestCases()));
        safe.setTestCaseResults(maskHiddenExecutionResults(safe.getTestCaseResults()));
        safe.setQuestionReviews(new ArrayList<>());

        return safe;
    }

    private AssessmentAssignment copyAssignment(AssessmentAssignment source) {
        AssessmentAssignment target = new AssessmentAssignment();

        target.setId(source.getId());
        target.setAssessmentId(source.getAssessmentId());
        target.setAssessmentTitle(source.getAssessmentTitle());
        target.setAssessmentType(source.getAssessmentType());
        target.setLanguage(source.getLanguage());
        target.setPrompt(source.getPrompt());
        target.setStarterCode(source.getStarterCode());
        target.setExpectedOutput(source.getExpectedOutput());
        target.setTestCases(copyTestCases(source.getTestCases()));
        target.setTestCaseResults(copyExecutionResults(source.getTestCaseResults()));
        target.setQuestionReviews(copyQuestionReviews(source.getQuestionReviews()));
        target.setSectionAttempts(copySectionAttempts(source.getSectionAttempts()));
        target.setSections(copySections(source.getSections(), true));
        target.setCandidateId(source.getCandidateId());
        target.setCandidateName(source.getCandidateName());
        target.setCandidateEmail(source.getCandidateEmail());
        target.setStatus(source.getStatus());
        target.setSubmittedAnswer(source.getSubmittedAnswer());
        target.setSubmittedCode(source.getSubmittedCode());
        target.setSubmittedAnswers(source.getSubmittedAnswers() == null
                ? new HashMap<>()
                : new HashMap<>(source.getSubmittedAnswers()));
        target.setAutoSubmitted(source.getAutoSubmitted());
        target.setExecutionStatus(source.getExecutionStatus());
        target.setActualOutput(source.getActualOutput());
        target.setExecutionError(source.getExecutionError());
        target.setScore(source.getScore());
        target.setFeedback(source.getFeedback());
        target.setAssignedAt(source.getAssignedAt());
        target.setDueAt(source.getDueAt());
        target.setTimeLimitMinutes(source.getTimeLimitMinutes());
        target.setStartedAt(source.getStartedAt());
        target.setExpiresAt(source.getExpiresAt());
        target.setSubmittedAt(source.getSubmittedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setGradedAt(source.getGradedAt());
        target.setMaxScore(source.getMaxScore());
        target.setOrganizationId(source.getOrganizationId());
        target.setOrganizationName(source.getOrganizationName());

        return target;
    }

    private List<AssessmentSectionAttempt> copySectionAttempts(List<AssessmentSectionAttempt> sectionAttempts) {
        if (sectionAttempts == null) {
            return new ArrayList<>();
        }

        return sectionAttempts.stream()
                .map(source -> {
                    AssessmentSectionAttempt copy = new AssessmentSectionAttempt();
                    copy.setSectionId(source.getSectionId());
                    copy.setSectionTitle(source.getSectionTitle());
                    copy.setTimeLimitMinutes(source.getTimeLimitMinutes());
                    copy.setStartedAt(source.getStartedAt());
                    copy.setExpiresAt(source.getExpiresAt());
                    copy.setCompletedAt(source.getCompletedAt());
                    return copy;
                })
                .toList();
    }

    private List<QuestionReview> normalizeQuestionReviews(
            AssessmentAssignment assignment,
            List<QuestionReviewRequest> requestedReviews
    ) {
        if (requestedReviews == null || requestedReviews.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, AssessmentQuestion> questionsById = new HashMap<>();

        if (assignment.getSections() != null) {
            assignment.getSections().stream()
                    .flatMap(section -> section.getQuestions() == null
                            ? List.<AssessmentQuestion>of().stream()
                            : section.getQuestions().stream())
                    .forEach(question -> questionsById.put(question.getId(), question));
        }

        List<QuestionReview> normalizedReviews = new ArrayList<>();

        for (QuestionReviewRequest requestedReview : requestedReviews) {
            if (requestedReview == null ||
                    requestedReview.getQuestionId() == null ||
                    requestedReview.getQuestionId().trim().isEmpty()) {
                continue;
            }

            AssessmentQuestion question = questionsById.get(requestedReview.getQuestionId());
            int maxPoints = question != null
                    ? safeInt(question.getPoints())
                    : Math.max(safeInt(requestedReview.getMaxPoints()), 0);
            int awardedPoints = Math.max(safeInt(requestedReview.getAwardedPoints()), 0);

            if (maxPoints > 0 && awardedPoints > maxPoints) {
                throw new IllegalArgumentException("Question review points cannot exceed question max points.");
            }

            QuestionReview normalizedReview = new QuestionReview();
            normalizedReview.setQuestionId(requestedReview.getQuestionId().trim());
            normalizedReview.setQuestionTitle(question != null
                    ? question.getTitle()
                    : safeString(requestedReview.getQuestionTitle()).trim());
            normalizedReview.setQuestionType(question != null
                    ? question.getType()
                    : requestedReview.getQuestionType());
            normalizedReview.setMaxPoints(maxPoints);
            normalizedReview.setAwardedPoints(awardedPoints);
            normalizedReview.setNotes(safeString(requestedReview.getNotes()).trim());
            normalizedReview.setReviewed(Boolean.TRUE.equals(requestedReview.getReviewed()));

            normalizedReviews.add(normalizedReview);
        }

        return normalizedReviews;
    }

    private List<AssessmentTestCase> normalizeAndValidateTestCases(
            AssessmentType type,
            int maxScore,
            String expectedOutput,
            List<AssessmentTestCase> testCases
    ) {
        if (type != AssessmentType.CODING_CHALLENGE) {
            return new ArrayList<>();
        }

        List<AssessmentTestCase> normalized = new ArrayList<>();

        if (testCases != null) {
            int index = 1;

            for (AssessmentTestCase testCase : testCases) {
                if (testCase == null) {
                    continue;
                }

                String expected = safeString(testCase.getExpectedOutput()).trim();

                if (expected.isEmpty()) {
                    continue;
                }

                AssessmentTestCase normalizedCase = new AssessmentTestCase();
                normalizedCase.setName(defaultName(testCase.getName(), index));
                normalizedCase.setInput(safeString(testCase.getInput()));
                normalizedCase.setExpectedOutput(expected);
                normalizedCase.setHidden(Boolean.TRUE.equals(testCase.getHidden()));
                normalizedCase.setPoints(safeInt(testCase.getPoints()));

                normalized.add(normalizedCase);
                index++;
            }
        }

        if (normalized.isEmpty() && expectedOutput != null && !expectedOutput.trim().isEmpty()) {
            AssessmentTestCase fallback = new AssessmentTestCase();
            fallback.setName("Default sample case");
            fallback.setInput("");
            fallback.setExpectedOutput(expectedOutput.trim());
            fallback.setHidden(false);
            fallback.setPoints(maxScore);

            normalized.add(fallback);
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one coding test case with expected output is required.");
        }

        boolean hasVisibleCase = normalized.stream()
                .anyMatch(testCase -> !testCase.isHidden());

        if (!hasVisibleCase) {
            throw new IllegalArgumentException("At least one visible sample test case is required.");
        }

        int totalPoints = normalized.stream()
                .mapToInt(testCase -> safeInt(testCase.getPoints()))
                .sum();

        if (totalPoints <= 0) {
            distributePoints(normalized, maxScore);
        } else if (totalPoints != maxScore) {
            throw new IllegalArgumentException("Total test case points must equal max score.");
        }

        return normalized;
    }

    private List<AssessmentSection> normalizeAndValidateSections(CreateAssessmentRequest request) {
        List<AssessmentSection> sourceSections = request.getSections();

        if (sourceSections == null || sourceSections.isEmpty()) {
            AssessmentQuestion legacyQuestion = new AssessmentQuestion();
            legacyQuestion.setId(UUID.randomUUID().toString());
            legacyQuestion.setType(toQuestionType(request.getType()));
            legacyQuestion.setTitle(request.getTitle());
            legacyQuestion.setPrompt(request.getPrompt());
            legacyQuestion.setPoints(request.getMaxScore());
            legacyQuestion.setLanguage(resolveLanguage(request));
            legacyQuestion.setStarterCode(request.getStarterCode());
            legacyQuestion.setExpectedOutput(request.getExpectedOutput());
            legacyQuestion.setTestCases(request.getTestCases());

            AssessmentSection legacySection = new AssessmentSection();
            legacySection.setId(UUID.randomUUID().toString());
            legacySection.setTitle("Assessment");
            legacySection.setDescription(request.getDescription());
            legacySection.setTimeLimitMinutes(request.getDurationMinutes());
            legacySection.setQuestions(List.of(legacyQuestion));

            sourceSections = List.of(legacySection);
        }

        List<AssessmentSection> normalizedSections = new ArrayList<>();

        for (int sectionIndex = 0; sectionIndex < sourceSections.size(); sectionIndex++) {
            AssessmentSection sourceSection = sourceSections.get(sectionIndex);

            if (sourceSection == null) {
                continue;
            }

            List<AssessmentQuestion> questions = normalizeQuestions(sourceSection.getQuestions(), sectionIndex);

            if (questions.isEmpty()) {
                continue;
            }

            AssessmentSection section = new AssessmentSection();
            section.setId(defaultId(sourceSection.getId()));
            section.setTitle(defaultSectionTitle(sourceSection.getTitle(), sectionIndex + 1));
            section.setDescription(sourceSection.getDescription());
            section.setTimeLimitMinutes(sourceSection.getTimeLimitMinutes());
            section.setQuestions(questions);

            normalizedSections.add(section);
        }

        if (normalizedSections.isEmpty()) {
            throw new IllegalArgumentException("At least one section with one question is required.");
        }

        return normalizedSections;
    }

    private List<AssessmentQuestion> normalizeQuestions(
            List<AssessmentQuestion> sourceQuestions,
            int sectionIndex
    ) {
        if (sourceQuestions == null) {
            return List.of();
        }

        List<AssessmentQuestion> normalizedQuestions = new ArrayList<>();

        for (int questionIndex = 0; questionIndex < sourceQuestions.size(); questionIndex++) {
            AssessmentQuestion sourceQuestion = sourceQuestions.get(questionIndex);

            if (sourceQuestion == null || sourceQuestion.getPrompt() == null || sourceQuestion.getPrompt().trim().isEmpty()) {
                continue;
            }

            QuestionType type = sourceQuestion.getType() == null
                    ? QuestionType.SHORT_ANSWER
                    : sourceQuestion.getType();

            AssessmentQuestion question = new AssessmentQuestion();
            question.setId(defaultId(sourceQuestion.getId()));
            question.setType(type);
            question.setTitle(defaultQuestionTitle(sourceQuestion, sectionIndex, questionIndex));
            question.setPrompt(sourceQuestion.getPrompt().trim());
            question.setPoints(Math.max(safeInt(sourceQuestion.getPoints()), 1));
            question.setLanguage(resolveQuestionLanguage(type, sourceQuestion.getLanguage()));
            question.setStarterCode(sourceQuestion.getStarterCode());
            question.setExpectedOutput(sourceQuestion.getExpectedOutput());
            question.setCorrectAnswer(sourceQuestion.getCorrectAnswer());
            question.setOptions(normalizeOptions(type, sourceQuestion.getOptions()));

            if (type == QuestionType.CODING_CHALLENGE) {
                question.setTestCases(normalizeAndValidateTestCases(
                        AssessmentType.CODING_CHALLENGE,
                        safeInt(question.getPoints()),
                        sourceQuestion.getExpectedOutput(),
                        sourceQuestion.getTestCases()
                ));
            } else {
                question.setTestCases(new ArrayList<>());
            }

            normalizedQuestions.add(question);
        }

        return normalizedQuestions;
    }

    private List<AssessmentQuestionOption> normalizeOptions(
            QuestionType type,
            List<AssessmentQuestionOption> options
    ) {
        if (type != QuestionType.MULTIPLE_CHOICE) {
            return new ArrayList<>();
        }

        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("Multiple choice questions require at least two options.");
        }

        List<AssessmentQuestionOption> normalizedOptions = new ArrayList<>();
        int correctCount = 0;

        for (AssessmentQuestionOption sourceOption : options) {
            if (sourceOption == null || sourceOption.getText() == null || sourceOption.getText().trim().isEmpty()) {
                continue;
            }

            AssessmentQuestionOption option = new AssessmentQuestionOption();
            option.setId(defaultId(sourceOption.getId()));
            option.setText(sourceOption.getText().trim());
            option.setCorrect(Boolean.TRUE.equals(sourceOption.getCorrect()));

            if (Boolean.TRUE.equals(option.getCorrect())) {
                correctCount++;
            }

            normalizedOptions.add(option);
        }

        if (normalizedOptions.size() < 2) {
            throw new IllegalArgumentException("Multiple choice questions require at least two valid options.");
        }

        if (correctCount != 1) {
            throw new IllegalArgumentException("Multiple choice questions require exactly one correct option.");
        }

        return normalizedOptions;
    }

    private AssessmentQuestion findPrimaryQuestion(List<AssessmentSection> sections) {
        if (sections == null) {
            return null;
        }

        return sections.stream()
                .flatMap(section -> section.getQuestions().stream())
                .filter(question -> question.getType() == QuestionType.CODING_CHALLENGE)
                .findFirst()
                .or(() -> sections.stream()
                        .flatMap(section -> section.getQuestions().stream())
                        .findFirst())
                .orElse(null);
    }

    private AssessmentType resolveAssessmentType(
            CreateAssessmentRequest request,
            AssessmentQuestion primaryQuestion
    ) {
        if (primaryQuestion != null && primaryQuestion.getType() == QuestionType.CODING_CHALLENGE) {
            return AssessmentType.CODING_CHALLENGE;
        }

        return request.getType() == null || request.getType() == AssessmentType.QUIZ
                ? AssessmentType.MCQ
                : request.getType();
    }

    private int resolveMaxScore(
            CreateAssessmentRequest request,
            List<AssessmentSection> sections
    ) {
        int total = sections.stream()
                .flatMap(section -> section.getQuestions().stream())
                .mapToInt(question -> safeInt(question.getPoints()))
                .sum();

        return total > 0 ? total : request.getMaxScore();
    }

    private QuestionType toQuestionType(AssessmentType type) {
        return type == AssessmentType.CODING_CHALLENGE
                ? QuestionType.CODING_CHALLENGE
                : QuestionType.SHORT_ANSWER;
    }

    private ProgrammingLanguage resolveQuestionLanguage(
            QuestionType type,
            ProgrammingLanguage language
    ) {
        if (type != QuestionType.CODING_CHALLENGE) {
            return ProgrammingLanguage.TEXT;
        }

        return language == null || language == ProgrammingLanguage.TEXT
                ? ProgrammingLanguage.JAVA
                : language;
    }

    private void distributePoints(List<AssessmentTestCase> testCases, int maxScore) {
        if (testCases == null || testCases.isEmpty()) {
            return;
        }

        int basePoints = Math.max(maxScore / testCases.size(), 1);
        int remaining = maxScore;

        for (int index = 0; index < testCases.size(); index++) {
            int points = index == testCases.size() - 1
                    ? remaining
                    : Math.min(basePoints, remaining);

            testCases.get(index).setPoints(points);
            remaining -= points;
        }
    }

    private List<AssessmentTestCase> getExecutableTestCasesForAssignment(AssessmentAssignment assignment) {
        List<AssessmentTestCase> testCases = assignment.getTestCases();

        if (testCases != null && !testCases.isEmpty()) {
            return testCases;
        }

        if (assignment.getExpectedOutput() != null && !assignment.getExpectedOutput().trim().isEmpty()) {
            AssessmentTestCase fallback = new AssessmentTestCase();
            fallback.setName("Default sample case");
            fallback.setInput("");
            fallback.setExpectedOutput(assignment.getExpectedOutput().trim());
            fallback.setHidden(false);
            fallback.setPoints(assignment.getMaxScore() != null ? assignment.getMaxScore() : 100);

            return List.of(fallback);
        }

        return List.of();
    }

    private List<AssessmentTestCase> maskHiddenTestCases(List<AssessmentTestCase> testCases) {
        if (testCases == null) {
            return new ArrayList<>();
        }

        return testCases.stream()
                .map(testCase -> {
                    AssessmentTestCase copy = copyTestCase(testCase);

                    if (copy.isHidden()) {
                        copy.setInput(null);
                        copy.setExpectedOutput(null);
                    }

                    return copy;
                })
                .toList();
    }

    private List<TestCaseExecutionResult> maskHiddenExecutionResults(List<TestCaseExecutionResult> results) {
        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream()
                .map(result -> {
                    TestCaseExecutionResult copy = copyExecutionResult(result);

                    if (Boolean.TRUE.equals(copy.getHidden())) {
                        maskHiddenResult(copy);
                    }

                    return copy;
                })
                .toList();
    }

    private void maskHiddenResult(TestCaseExecutionResult result) {
        result.setInput(null);
        result.setExpectedOutput(null);
        result.setActualOutput(null);
        result.setError(null);
    }

    private List<AssessmentTestCase> copyTestCases(List<AssessmentTestCase> testCases) {
        if (testCases == null) {
            return new ArrayList<>();
        }

        return testCases.stream()
                .map(this::copyTestCase)
                .toList();
    }

    private List<AssessmentSection> copySections(
            List<AssessmentSection> sections,
            boolean candidateView
    ) {
        if (sections == null) {
            return new ArrayList<>();
        }

        return sections.stream()
                .map(section -> copySection(section, candidateView))
                .toList();
    }

    private AssessmentSection copySection(
            AssessmentSection source,
            boolean candidateView
    ) {
        AssessmentSection copy = new AssessmentSection();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setTimeLimitMinutes(source.getTimeLimitMinutes());
        copy.setQuestions(
                source.getQuestions() == null
                        ? new ArrayList<>()
                        : source.getQuestions()
                                .stream()
                                .map(question -> copyQuestion(question, candidateView))
                                .toList()
        );

        return copy;
    }

    private AssessmentQuestion copyQuestion(
            AssessmentQuestion source,
            boolean candidateView
    ) {
        AssessmentQuestion copy = new AssessmentQuestion();
        copy.setId(source.getId());
        copy.setType(source.getType());
        copy.setTitle(source.getTitle());
        copy.setPrompt(source.getPrompt());
        copy.setPoints(source.getPoints());
        copy.setLanguage(source.getLanguage());
        copy.setStarterCode(source.getStarterCode());
        copy.setExpectedOutput(candidateView ? null : source.getExpectedOutput());
        copy.setCorrectAnswer(candidateView ? null : source.getCorrectAnswer());
        copy.setOptions(copyOptions(source.getOptions(), candidateView));
        copy.setTestCases(candidateView
                ? maskHiddenTestCases(source.getTestCases())
                : copyTestCases(source.getTestCases()));

        return copy;
    }

    private List<AssessmentQuestionOption> copyOptions(
            List<AssessmentQuestionOption> options,
            boolean candidateView
    ) {
        if (options == null) {
            return new ArrayList<>();
        }

        return options.stream()
                .map(source -> {
                    AssessmentQuestionOption copy = new AssessmentQuestionOption();
                    copy.setId(source.getId());
                    copy.setText(source.getText());
                    copy.setCorrect(candidateView ? null : source.getCorrect());
                    return copy;
                })
                .toList();
    }

    private AssessmentTestCase copyTestCase(AssessmentTestCase source) {
        AssessmentTestCase copy = new AssessmentTestCase();

        copy.setName(source.getName());
        copy.setInput(source.getInput());
        copy.setExpectedOutput(source.getExpectedOutput());
        copy.setHidden(source.getHidden());
        copy.setPoints(source.getPoints());

        return copy;
    }

    private List<TestCaseExecutionResult> copyExecutionResults(List<TestCaseExecutionResult> results) {
        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream()
                .map(this::copyExecutionResult)
                .toList();
    }

    private List<QuestionReview> copyQuestionReviews(List<QuestionReview> questionReviews) {
        if (questionReviews == null) {
            return new ArrayList<>();
        }

        return questionReviews.stream()
                .map(source -> {
                    QuestionReview copy = new QuestionReview();
                    copy.setQuestionId(source.getQuestionId());
                    copy.setQuestionTitle(source.getQuestionTitle());
                    copy.setQuestionType(source.getQuestionType());
                    copy.setMaxPoints(source.getMaxPoints());
                    copy.setAwardedPoints(source.getAwardedPoints());
                    copy.setNotes(source.getNotes());
                    copy.setReviewed(source.getReviewed());
                    return copy;
                })
                .toList();
    }

    private TestCaseExecutionResult copyExecutionResult(TestCaseExecutionResult source) {
        TestCaseExecutionResult copy = new TestCaseExecutionResult();

        copy.setName(source.getName());
        copy.setInput(source.getInput());
        copy.setExpectedOutput(source.getExpectedOutput());
        copy.setActualOutput(source.getActualOutput());
        copy.setError(source.getError());
        copy.setExitCode(source.getExitCode());
        copy.setTimedOut(source.getTimedOut());
        copy.setPassed(source.getPassed());
        copy.setHidden(source.getHidden());
        copy.setPoints(source.getPoints());
        copy.setAwardedPoints(source.getAwardedPoints());

        return copy;
    }

    private String buildActualOutputSummary(List<TestCaseExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (TestCaseExecutionResult result : results) {
            builder.append(result.getName())
                    .append(" [")
                    .append(Boolean.TRUE.equals(result.getPassed()) ? "PASSED" : "FAILED")
                    .append("]\n")
                    .append(safeString(result.getActualOutput()))
                    .append("\n\n");
        }

        return builder.toString().trim();
    }

    private String buildExecutionErrorSummary(List<TestCaseExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (TestCaseExecutionResult result : results) {
            if (result.getError() != null && !result.getError().isBlank()) {
                builder.append(result.getName())
                        .append(":\n")
                        .append(result.getError())
                        .append("\n\n");
            }
        }

        return builder.toString().trim();
    }

    private ProgrammingLanguage resolveLanguage(CreateAssessmentRequest request) {
        if (request.getType() == AssessmentType.QUIZ || request.getType() == AssessmentType.MCQ) {
            return ProgrammingLanguage.TEXT;
        }

        if (request.getLanguage() == null || request.getLanguage() == ProgrammingLanguage.TEXT) {
            return ProgrammingLanguage.JAVA;
        }

        return request.getLanguage();
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

    private void validateAssignmentSchedule(AssignAssessmentRequest request) {
        if (request.getDueAt() != null && !request.getDueAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Due date must be in the future.");
        }

        Integer timeLimitMinutes = request.getTimeLimitMinutes();

        if (timeLimitMinutes != null && timeLimitMinutes > 480) {
            throw new IllegalArgumentException("Time limit cannot exceed 480 minutes.");
        }
    }

    private void validateAssignmentCanBeWorkedOn(AssessmentAssignment assignment) {
        validateAssignmentCanBeWorkedOn(assignment, false);
    }

    private void validateAssignmentCanBeWorkedOn(AssessmentAssignment assignment, boolean allowAutoSubmitGrace) {
        validateAssignmentDueDateOpen(assignment);

        if (assignment.getTimeLimitMinutes() != null && assignment.getStartedAt() == null) {
            throw new IllegalArgumentException("Start the assessment before working on it.");
        }

        Instant now = Instant.now();

        if (assignment.getExpiresAt() != null &&
                now.isAfter(assignment.getExpiresAt()) &&
                !isWithinAutoSubmitGrace(assignment, now, allowAutoSubmitGrace)) {
            throw new IllegalArgumentException("Assessment time limit has expired.");
        }
    }

    private void validateCodingSectionOpen(AssessmentAssignment assignment) {
        AssessmentSection codingSection = findCodingSection(assignment);

        if (codingSection == null) {
            return;
        }

        AssessmentSectionAttempt attempt = findSectionAttempt(assignment, codingSection.getId());

        if (attempt == null) {
            throw new IllegalArgumentException("Start the coding section before running code.");
        }

        validateSectionAttemptOpen(attempt, false);
    }

    private void validateSubmittedSections(AssessmentAssignment assignment, boolean allowAutoSubmitGrace) {
        if (assignment.getSections() == null || assignment.getSections().isEmpty()) {
            return;
        }

        List<AssessmentSection> sections = getAssignmentSectionsOrLegacySection(assignment);

        if (sections.isEmpty()) {
            return;
        }

        for (AssessmentSection section : sections) {
            AssessmentSectionAttempt attempt = findSectionAttempt(assignment, section.getId());

            if (attempt == null) {
                throw new IllegalArgumentException("Start every section before submitting the assessment.");
            }

            validateSectionAttemptOpen(attempt, allowAutoSubmitGrace);
        }
    }

    private void validateSectionAttemptOpen(
            AssessmentSectionAttempt attempt,
            boolean allowAutoSubmitGrace
    ) {
        if (attempt.getExpiresAt() == null) {
            return;
        }

        Instant now = Instant.now();
        Instant effectiveEnd = attempt.getCompletedAt() != null ? attempt.getCompletedAt() : now;

        if (effectiveEnd.isAfter(attempt.getExpiresAt()) &&
                !(allowAutoSubmitGrace &&
                        !effectiveEnd.isAfter(attempt.getExpiresAt().plus(AUTO_SUBMIT_GRACE_SECONDS, ChronoUnit.SECONDS)))) {
            throw new IllegalArgumentException("Section time limit has expired.");
        }
    }

    private void markOpenSectionAttemptsCompleted(AssessmentAssignment assignment, Instant completedAt) {
        if (assignment.getSectionAttempts() == null) {
            return;
        }

        assignment.getSectionAttempts().stream()
                .filter(attempt -> attempt.getCompletedAt() == null)
                .forEach(attempt -> attempt.setCompletedAt(completedAt));
    }

    private AssessmentSection findAssignmentSection(AssessmentAssignment assignment, String sectionId) {
        return getAssignmentSectionsOrLegacySection(assignment)
                .stream()
                .filter(section -> section.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Assessment section not found."));
    }

    private AssessmentSection findCodingSection(AssessmentAssignment assignment) {
        return getAssignmentSectionsOrLegacySection(assignment)
                .stream()
                .filter(section -> section.getQuestions() != null &&
                        section.getQuestions().stream()
                                .anyMatch(question -> question.getType() == QuestionType.CODING_CHALLENGE))
                .findFirst()
                .orElse(null);
    }

    private AssessmentSectionAttempt findSectionAttempt(AssessmentAssignment assignment, String sectionId) {
        if (assignment.getSectionAttempts() == null) {
            assignment.setSectionAttempts(new ArrayList<>());
        }

        return assignment.getSectionAttempts()
                .stream()
                .filter(attempt -> sectionId.equals(attempt.getSectionId()))
                .findFirst()
                .orElse(null);
    }

    private List<AssessmentSection> getAssignmentSectionsOrLegacySection(AssessmentAssignment assignment) {
        if (assignment.getSections() != null && !assignment.getSections().isEmpty()) {
            return assignment.getSections();
        }

        AssessmentSection legacySection = new AssessmentSection();
        legacySection.setId("legacy-section");
        legacySection.setTitle("Assessment");
        legacySection.setDescription(assignment.getPrompt());
        legacySection.setTimeLimitMinutes(assignment.getTimeLimitMinutes());

        AssessmentQuestion legacyQuestion = new AssessmentQuestion();
        legacyQuestion.setId("legacy-question");
        legacyQuestion.setType(assignment.getAssessmentType() == AssessmentType.CODING_CHALLENGE
                ? QuestionType.CODING_CHALLENGE
                : QuestionType.SHORT_ANSWER);
        legacyQuestion.setTitle(assignment.getAssessmentTitle());
        legacyQuestion.setPrompt(assignment.getPrompt());
        legacyQuestion.setPoints(assignment.getMaxScore());
        legacyQuestion.setLanguage(assignment.getLanguage());

        legacySection.setQuestions(List.of(legacyQuestion));
        return List.of(legacySection);
    }

    private boolean isWithinAutoSubmitGrace(
            AssessmentAssignment assignment,
            Instant now,
            boolean allowAutoSubmitGrace
    ) {
        return allowAutoSubmitGrace &&
                assignment.getExpiresAt() != null &&
                !now.isAfter(assignment.getExpiresAt().plus(AUTO_SUBMIT_GRACE_SECONDS, ChronoUnit.SECONDS));
    }

    private void validateAssignmentDueDateOpen(AssessmentAssignment assignment) {
        if (assignment.getDueAt() != null && Instant.now().isAfter(assignment.getDueAt())) {
            throw new IllegalArgumentException("Assignment due date has passed.");
        }
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

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found."));
    }

    private Map<String, String> normalizeSubmittedAnswers(SubmitAssignmentRequest request) {
        Map<String, String> normalized = new HashMap<>();

        if (request.getSubmittedAnswers() != null) {
            request.getSubmittedAnswers().forEach((questionId, answer) -> {
                if (questionId != null && answer != null && !answer.trim().isEmpty()) {
                    normalized.put(questionId, answer.trim());
                }
            });
        }

        if (normalized.isEmpty() &&
                request.getSubmittedAnswer() != null &&
                !request.getSubmittedAnswer().trim().isEmpty()) {
            normalized.put("legacy-answer", request.getSubmittedAnswer().trim());
        }

        return normalized;
    }

    private String resolveSubmittedAnswerSummary(Map<String, String> submittedAnswers) {
        if (submittedAnswers == null || submittedAnswers.isEmpty()) {
            return null;
        }

        if (submittedAnswers.size() == 1) {
            return submittedAnswers.values().iterator().next();
        }

        StringBuilder builder = new StringBuilder();

        submittedAnswers.forEach((questionId, answer) ->
                builder.append(questionId).append(": ").append(answer).append("\n")
        );

        return builder.toString().trim();
    }

    private void applyAutoScoreForObjectiveQuestions(
            AssessmentAssignment assignment,
            Map<String, String> submittedAnswers
    ) {
        int score = calculateObjectiveScore(assignment, submittedAnswers);

        if (!hasObjectiveQuestions(assignment)) {
            return;
        }

        assignment.setScore(score);
        assignment.setFeedback("Multiple choice questions were scored automatically. Short-answer responses still require review.");
    }

    private int calculateObjectiveScore(
            AssessmentAssignment assignment,
            Map<String, String> submittedAnswers
    ) {
        List<AssessmentQuestion> questions = assignment.getSections() == null
                ? List.of()
                : assignment.getSections()
                        .stream()
                        .flatMap(section -> section.getQuestions() == null
                                ? List.<AssessmentQuestion>of().stream()
                                : section.getQuestions().stream())
                        .toList();

        List<AssessmentQuestion> multipleChoiceQuestions = questions.stream()
                .filter(question -> question.getType() == QuestionType.MULTIPLE_CHOICE)
                .toList();

        if (multipleChoiceQuestions.isEmpty()) {
            return 0;
        }

        int score = 0;

        for (AssessmentQuestion question : multipleChoiceQuestions) {
            String submittedOptionId = submittedAnswers.get(question.getId());

            boolean correct = question.getOptions() != null &&
                    question.getOptions().stream()
                            .anyMatch(option ->
                                    Boolean.TRUE.equals(option.getCorrect()) &&
                                            option.getId().equals(submittedOptionId)
                            );

            if (correct) {
                score += safeInt(question.getPoints());
            }
        }

        return score;
    }

    private boolean hasObjectiveQuestions(AssessmentAssignment assignment) {
        if (assignment.getSections() == null) {
            return false;
        }

        return assignment.getSections()
                .stream()
                .flatMap(section -> section.getQuestions() == null
                        ? List.<AssessmentQuestion>of().stream()
                        : section.getQuestions().stream())
                .anyMatch(question -> question.getType() == QuestionType.MULTIPLE_CHOICE);
    }

    private String defaultName(String value, int index) {
        if (value == null || value.trim().isEmpty()) {
            return "Test case " + index;
        }

        return value.trim();
    }

    private String defaultQuestionTitle(
            AssessmentQuestion question,
            int sectionIndex,
            int questionIndex
    ) {
        if (question.getTitle() != null && !question.getTitle().trim().isEmpty()) {
            return question.getTitle().trim();
        }

        return "Question " + (sectionIndex + 1) + "." + (questionIndex + 1);
    }

    private String defaultSectionTitle(String value, int index) {
        if (value == null || value.trim().isEmpty()) {
            return "Section " + index;
        }

        return value.trim();
    }

    private String defaultId(String value) {
        return value == null || value.trim().isEmpty()
                ? UUID.randomUUID().toString()
                : value.trim();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
