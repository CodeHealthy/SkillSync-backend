package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class AssignAssessmentRequest {

    @NotBlank(message = "Assessment ID is required")
    private String assessmentId;

    @NotBlank(message = "Candidate ID is required")
    private String candidateId;

    public AssignAssessmentRequest() {
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }
}