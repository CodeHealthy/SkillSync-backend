package app.SkillSync.dto;

import app.SkillSync.model.AssessmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateAssessmentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Assessment type is required")
    private AssessmentType type;

    @Min(value = 1, message = "Max score must be at least 1")
    @Max(value = 1000, message = "Max score cannot exceed 1000")
    private int maxScore;

    @NotBlank(message = "Prompt is required")
    private String prompt;

    public CreateAssessmentRequest() {
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
}