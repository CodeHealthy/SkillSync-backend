package app.SkillSync.model;

import java.util.ArrayList;
import java.util.List;

public class AssessmentSection {

    private String id;
    private String title;
    private String description;
    private Integer timeLimitMinutes;
    private List<AssessmentQuestion> questions = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public List<AssessmentQuestion> getQuestions() {
        return questions;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public void setQuestions(List<AssessmentQuestion> questions) {
        this.questions = questions == null ? new ArrayList<>() : questions;
    }
}
