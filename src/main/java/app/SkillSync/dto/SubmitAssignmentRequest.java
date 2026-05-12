package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class SubmitAssignmentRequest {

    @NotBlank(message = "Submitted answer is required")
    private String submittedAnswer;

    public SubmitAssignmentRequest() {
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }
}