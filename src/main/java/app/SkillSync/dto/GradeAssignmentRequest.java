package app.SkillSync.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.ArrayList;
import java.util.List;

public class GradeAssignmentRequest {

    @Min(value = 0, message = "Score cannot be below 0")
    @Max(value = 1000, message = "Score cannot exceed 1000")
    private int score;

    private String feedback;
    private List<QuestionReviewRequest> questionReviews = new ArrayList<>();

    public GradeAssignmentRequest() {
    }

    public int getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public List<QuestionReviewRequest> getQuestionReviews() {
        return questionReviews;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public void setQuestionReviews(List<QuestionReviewRequest> questionReviews) {
        this.questionReviews = questionReviews == null ? new ArrayList<>() : questionReviews;
    }
}
