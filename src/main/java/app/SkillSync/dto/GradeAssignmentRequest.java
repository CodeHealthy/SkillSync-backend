package app.SkillSync.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class GradeAssignmentRequest {

    @Min(value = 0, message = "Score cannot be below 0")
    @Max(value = 1000, message = "Score cannot exceed 1000")
    private int score;

    private String feedback;

    public GradeAssignmentRequest() {
    }

    public int getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}