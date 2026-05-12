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

    private String candidateId;
    private String candidateName;
    private String candidateEmail;

    private AssignmentStatus status;

    private String submittedAnswer;
    private Integer score;
    private String feedback;

    private Instant assignedAt;
    private Instant submittedAt;

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

    public void setId(String id) {
        this.id = id;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void setAssessmentTitle(String assessmentTitle) {
        this.assessmentTitle = assessmentTitle;
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
}