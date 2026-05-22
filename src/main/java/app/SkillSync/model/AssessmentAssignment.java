package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "assessment_assignments")
public class AssessmentAssignment {

    @Id
    private String id;

    private String assessmentId;
    private String assessmentTitle;
    private AssessmentType assessmentType;
    private AssessmentStatus assessmentStatus;
    private ProgrammingLanguage language;

    private String prompt;
    private String starterCode;

    /**
     * Kept for backward compatibility with old single-output assessments.
     * New coding challenge grading should use testCases.
     */
    private String expectedOutput;

    private List<AssessmentTestCase> testCases = new ArrayList<>();
    private List<AssessmentSection> sections = new ArrayList<>();
    private List<TestCaseExecutionResult> testCaseResults = new ArrayList<>();
    private List<QuestionReview> questionReviews = new ArrayList<>();
    private List<AssessmentSectionAttempt> sectionAttempts = new ArrayList<>();

    private String candidateId;
    private String candidateName;
    private String candidateEmail;

    private AssignmentStatus status;

    private String submittedAnswer;
    private String submittedCode;
    private java.util.Map<String, String> submittedAnswers = new java.util.HashMap<>();
    private Boolean autoSubmitted;

    private String executionStatus;
    private String actualOutput;
    private String executionError;

    private Integer score;
    private String feedback;

    private Instant assignedAt;
    private Instant dueAt;
    private Integer timeLimitMinutes;
    private Instant startedAt;
    private Instant expiresAt;
    private Instant submittedAt;
    private Instant completedAt;
    private Instant gradedAt;
    private Integer maxScore;
    private String organizationId;
    private String organizationName;

    public AssessmentAssignment() {
    }

    public String getId() {
        return id;
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public String getAssessmentTitle() {
        return assessmentTitle;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public AssessmentStatus getAssessmentStatus() {
        return assessmentStatus;
    }

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public List<AssessmentTestCase> getTestCases() {
        return testCases;
    }

    public List<AssessmentSection> getSections() {
        return sections;
    }

    public List<TestCaseExecutionResult> getTestCaseResults() {
        return testCaseResults;
    }

    public List<QuestionReview> getQuestionReviews() {
        return questionReviews;
    }

    public List<AssessmentSectionAttempt> getSectionAttempts() {
        return sectionAttempts;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public String getCandidateEmail() {
        return candidateEmail;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public String getSubmittedCode() {
        return submittedCode;
    }

    public java.util.Map<String, String> getSubmittedAnswers() {
        return submittedAnswers;
    }

    public Boolean getAutoSubmitted() {
        return autoSubmitted;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public String getExecutionError() {
        return executionError;
    }

    public Integer getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void setAssessmentTitle(String assessmentTitle) {
        this.assessmentTitle = assessmentTitle;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public void setAssessmentStatus(AssessmentStatus assessmentStatus) {
        this.assessmentStatus = assessmentStatus;
    }

    public void setLanguage(ProgrammingLanguage language) {
        this.language = language;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setStarterCode(String starterCode) {
        this.starterCode = starterCode;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public void setTestCases(List<AssessmentTestCase> testCases) {
        this.testCases = testCases == null ? new ArrayList<>() : testCases;
    }

    public void setSections(List<AssessmentSection> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }

    public void setTestCaseResults(List<TestCaseExecutionResult> testCaseResults) {
        this.testCaseResults = testCaseResults == null ? new ArrayList<>() : testCaseResults;
    }

    public void setQuestionReviews(List<QuestionReview> questionReviews) {
        this.questionReviews = questionReviews == null ? new ArrayList<>() : questionReviews;
    }

    public void setSectionAttempts(List<AssessmentSectionAttempt> sectionAttempts) {
        this.sectionAttempts = sectionAttempts == null ? new ArrayList<>() : sectionAttempts;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public void setCandidateEmail(String candidateEmail) {
        this.candidateEmail = candidateEmail;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public void setSubmittedCode(String submittedCode) {
        this.submittedCode = submittedCode;
    }

    public void setSubmittedAnswers(java.util.Map<String, String> submittedAnswers) {
        this.submittedAnswers = submittedAnswers == null ? new java.util.HashMap<>() : submittedAnswers;
    }

    public void setAutoSubmitted(Boolean autoSubmitted) {
        this.autoSubmitted = autoSubmitted;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }

    public void setExecutionError(String executionError) {
        this.executionError = executionError;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void setGradedAt(Instant gradedAt) {
        this.gradedAt = gradedAt;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }
}
