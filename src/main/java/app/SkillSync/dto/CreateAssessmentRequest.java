package app.SkillSync.dto;

import app.SkillSync.model.AssessmentTestCase;
import app.SkillSync.model.AssessmentType;
import app.SkillSync.model.AssessmentSection;
import app.SkillSync.model.AssessmentStatus;
import app.SkillSync.model.ProgrammingLanguage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CreateAssessmentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private String roleTitle;
    private AssessmentStatus status;

    @NotNull(message = "Assessment type is required")
    private AssessmentType type;

    private ProgrammingLanguage language;

    @Min(value = 1, message = "Max score must be at least 1")
    @Max(value = 1000, message = "Max score cannot exceed 1000")
    private int maxScore;
    private Integer durationMinutes;

    @NotBlank(message = "Prompt is required")
    private String prompt;

    private String starterCode;

    /**
     * Kept for backward compatibility.
     * New coding challenges should use testCases.
     */
    private String expectedOutput;

    @Valid
    private List<AssessmentTestCase> testCases = new ArrayList<>();
    @Valid
    private List<AssessmentSection> sections = new ArrayList<>();

    public CreateAssessmentRequest() {
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public AssessmentStatus getStatus() {
        return status;
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

    public Integer getDurationMinutes() {
        return durationMinutes;
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

    public List<AssessmentTestCase> getTestCases() {
        return testCases;
    }

    public List<AssessmentSection> getSections() {
        return sections;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
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

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
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

    public void setTestCases(List<AssessmentTestCase> testCases) {
        this.testCases = testCases == null ? new ArrayList<>() : testCases;
    }

    public void setSections(List<AssessmentSection> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }
}
