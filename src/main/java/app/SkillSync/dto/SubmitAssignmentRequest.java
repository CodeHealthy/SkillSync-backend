package app.SkillSync.dto;

public class SubmitAssignmentRequest {

    private String submittedAnswer;

    private String submittedCode;

    private Boolean autoSubmitted;

    public SubmitAssignmentRequest() {
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public String getSubmittedCode() {
        return submittedCode;
    }

    public Boolean getAutoSubmitted() {
        return autoSubmitted;
    }

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public void setSubmittedCode(String submittedCode) {
        this.submittedCode = submittedCode;
    }

    public void setAutoSubmitted(Boolean autoSubmitted) {
        this.autoSubmitted = autoSubmitted;
    }
}
