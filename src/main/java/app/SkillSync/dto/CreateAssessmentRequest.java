package app.SkillSync.dto;

import app.SkillSync.model.AssessmentType;
import app.SkillSync.model.ProgrammingLanguage;
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

    private ProgrammingLanguage language;

    @Min(value = 1, message = "Max score must be at least 1")
    @Max(value = 1000, message = "Max score cannot exceed 1000")
    private int maxScore;

    @NotBlank(message = "Prompt is required")
    private String prompt;

    private String starterCode;

    private String expectedOutput;

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

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public String getExpectedOutput() {
        return expectedOutput;
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

    public void setLanguage(ProgrammingLanguage language) {
        this.language = language;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setStarterCode(String starterCode) {
        this.starterCode = starterCode;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }
}