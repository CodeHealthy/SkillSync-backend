package app.SkillSync.model;

public class AssessmentQuestionOption {

    private String id;
    private String text;
    private Boolean correct;

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }
}
