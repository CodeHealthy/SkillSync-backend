package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public class AssignAssessmentRequest {

    @NotBlank(message = "Assessment ID is required")
    private String assessmentId;

    @NotBlank(message = "Candidate ID is required")
    private String candidateId;

    private Instant dueAt;

    @Positive(message = "Time limit must be greater than zero")
    private Integer timeLimitMinutes;

    public AssignAssessmentRequest() {
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }
}
