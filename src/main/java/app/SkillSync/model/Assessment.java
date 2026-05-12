package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "assessments")
public class Assessment {

    @Id
    private String id;

    private String title;
    private String description;
    private AssessmentType type;
    private int maxScore;
    private String prompt;
    private Instant createdAt;

    public Assessment() {
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public AssessmentType getType() {
        return type;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public String getPrompt() {
        return prompt;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public void setType(AssessmentType type) {
        this.type = type;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}