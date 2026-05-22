package app.SkillSync.dto;

import app.SkillSync.model.QuestionType;

public class QuestionReviewRequest {

    private String questionId;
    private String questionTitle;
    private QuestionType questionType;
    private Integer maxPoints;
    private Integer awardedPoints;
    private String notes;
    private Boolean reviewed;

    public QuestionReviewRequest() {
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getQuestionTitle() {
        return questionTitle;
    }

    public void setQuestionTitle(String questionTitle) {
        this.questionTitle = questionTitle;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public Integer getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Integer maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Integer getAwardedPoints() {
        return awardedPoints;
    }

    public void setAwardedPoints(Integer awardedPoints) {
        this.awardedPoints = awardedPoints;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getReviewed() {
        return reviewed;
    }

    public void setReviewed(Boolean reviewed) {
        this.reviewed = reviewed;
    }
}
