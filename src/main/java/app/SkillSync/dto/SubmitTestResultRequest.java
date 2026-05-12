package app.SkillSync.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class SubmitTestResultRequest {

    @NotBlank(message = "Test name is required")
    private String testName;

    @Min(value = 0, message = "Score cannot be below 0")
    @Max(value = 100, message = "Score cannot be above 100")
    private int score;

    @NotBlank(message = "Status is required")
    private String status;

    private String answers;

    public SubmitTestResultRequest() {
    }

    public String getTestName() {
        return testName;
    }

    public int getScore() {
        return score;
    }

    public String getStatus() {
        return status;
    }

    public String getAnswers() {
        return answers;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAnswers(String answers) {
        this.answers = answers;
    }
}