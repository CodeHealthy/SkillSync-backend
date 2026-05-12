package app.SkillSync.dto;

public class SubmitAssignmentRequest {

    private String submittedAnswer;

    private String submittedCode;

    public SubmitAssignmentRequest() {
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public String getSubmittedCode() {
        return submittedCode;
    }

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public void setSubmittedCode(String submittedCode) {
        this.submittedCode = submittedCode;
    }
}