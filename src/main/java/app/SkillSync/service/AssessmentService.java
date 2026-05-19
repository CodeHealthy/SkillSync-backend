package app.SkillSync.service;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.AssignmentRunResult;
import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.dto.RunCodeRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.dto.TestCaseRunSummary;
import app.SkillSync.model.Assessment;
import app.SkillSync.model.AssessmentAssignment;
import app.SkillSync.model.AssessmentTestCase;
import app.SkillSync.model.AssessmentType;
import app.SkillSync.model.AssignmentStatus;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Organization;
import app.SkillSync.model.ProgrammingLanguage;
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
import java.util.ArrayList;
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
        User adminUser = getCurrentUser();

        if (adminUser.getOrganizationId() == null || adminUser.getOrganizationId().isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        ProgrammingLanguage resolvedLanguage = resolveLanguage(request);
        List<AssessmentTestCase> normalizedTestCases = normalizeAndValidateTestCases(
                request.getType(),
                request.getMaxScore(),
                request.getExpectedOutput(),
                request.getTestCases()
        );

        Assessment assessment = new Assessment();
        assessment.setTitle(request.getTitle().trim());
        assessment.setDescription(request.getDescription());
        assessment.setType(request.getType());
        assessment.setLanguage(resolvedLanguage);
        assessment.setMaxScore(request.getMaxScore());
        assessment.setPrompt(request.getPrompt().trim());
        assessment.setStarterCode(request.getStarterCode());
        assessment.setExpectedOutput(request.getExpectedOutput());
        assessment.setTestCases(normalizedTestCases);
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
        assignment.setTestCases(copyTestCases(assessment.getTestCases()));
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
                .map(this::toCandidateSafeAssignment)
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
            assignment.setTestCaseResults(new ArrayList<>());
            assignment.setActualOutput(null);
            assignment.setExecutionError(null);
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

        assignment.setScore(request.getScore());
        assignment.setFeedback(request.getFeedback());
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

        int awardedPoints = testCaseResults.stream()
                .mapToInt(result -> safeInt(result.getAwardedPoints()))
                .sum();

        int totalPoints = testCaseResults.stream()
                .mapToInt(result -> safeInt(result.getPoints()))
                .sum();

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
                            + " test cases. Score: " + awardedPoints + "/" + totalPoints + "."
            );
        } else if (anyRuntimeError) {
            assignment.setExecutionStatus("ERROR");
            assignment.setFeedback(
                    "Automatic grading completed with execution errors. Passed "
                            + passedTests + "/" + testCaseResults.size()
                            + " test cases. Score: " + awardedPoints + "/" + totalPoints + "."
            );
        } else {
            assignment.setExecutionStatus("FAILED");
            assignment.setFeedback(
                    "Automatic grading completed. Passed "
                            + passedTests + "/" + testCaseResults.size()
                            + " test cases. Score: " + awardedPoints + "/" + totalPoints + "."
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

        if (assignment.getAssessmentType() != AssessmentType.CODING_CHALLENGE) {
            throw new IllegalArgumentException("Only coding challenges can be executed");
        }

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only assigned assessments can be run before submission");
        }

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Source code is required");
        }

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
        target.setCandidateId(source.getCandidateId());
        target.setCandidateName(source.getCandidateName());
        target.setCandidateEmail(source.getCandidateEmail());
        target.setStatus(source.getStatus());
        target.setSubmittedAnswer(source.getSubmittedAnswer());
        target.setSubmittedCode(source.getSubmittedCode());
        target.setExecutionStatus(source.getExecutionStatus());
        target.setActualOutput(source.getActualOutput());
        target.setExecutionError(source.getExecutionError());
        target.setScore(source.getScore());
        target.setFeedback(source.getFeedback());
        target.setAssignedAt(source.getAssignedAt());
        target.setSubmittedAt(source.getSubmittedAt());
        target.setGradedAt(source.getGradedAt());
        target.setMaxScore(source.getMaxScore());
        target.setOrganizationId(source.getOrganizationId());
        target.setOrganizationName(source.getOrganizationName());

        return target;
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
        if (request.getType() == AssessmentType.QUIZ) {
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

    private String defaultName(String value, int index) {
        if (value == null || value.trim().isEmpty()) {
            return "Test case " + index;
        }

        return value.trim();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}