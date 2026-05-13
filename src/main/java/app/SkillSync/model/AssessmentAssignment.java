package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "assessment_assignments")
public class AssessmentAssignment {

    @Id
    private String id;

    private String assessmentId;
    private String assessmentTitle;
    private AssessmentType assessmentType;
    private ProgrammingLanguage language;

    private String prompt;
    private String starterCode;
    private String expectedOutput;

    private String candidateId;
    private String candidateName;
    private String candidateEmail;

    private AssignmentStatus status;

    private String submittedAnswer;
    private String submittedCode;

    private String executionStatus;
    private String actualOutput;
    private String executionError;

    private Integer score;
    private String feedback;

    private Instant assignedAt;
    private Instant submittedAt;
    private Instant gradedAt;
    private Integer maxScore;
    private String organizationId;
    private String organizationName;

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

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

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getGradedAt() {
        return gradedAt;
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

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public void setGradedAt(Instant gradedAt) {
        this.gradedAt = gradedAt;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }
}