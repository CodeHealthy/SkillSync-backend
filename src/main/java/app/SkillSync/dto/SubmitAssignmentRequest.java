package app.SkillSync.dto;

public class SubmitAssignmentRequest {

    private String submittedAnswer;

    private String submittedCode;
    private java.util.Map<String, String> submittedAnswers;

    private Boolean autoSubmitted;

    public SubmitAssignmentRequest() {
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

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public void setSubmittedCode(String submittedCode) {
        this.submittedCode = submittedCode;
    }

    public void setSubmittedAnswers(java.util.Map<String, String> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
    }

    public void setAutoSubmitted(Boolean autoSubmitted) {
        this.autoSubmitted = autoSubmitted;
    }
}
