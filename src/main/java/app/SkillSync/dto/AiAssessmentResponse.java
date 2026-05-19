package app.SkillSync.dto;

import app.SkillSync.model.AssessmentTestCase;

import java.util.ArrayList;
import java.util.List;

public class AiAssessmentResponse {

    private String title;
    private String description;
    private String prompt;
    private String starterCode;

    /**
     * Kept for backward compatibility.
     * For new coding challenges, testCases should drive grading.
     */
    private String expectedOutput;

    private Integer maxScore;
    private String rubric;

    private List<AssessmentTestCase> testCases = new ArrayList<>();

    public AiAssessmentResponse() {
    }

    public AiAssessmentResponse(
            String title,
            String description,
            String prompt,
            String starterCode,
            String expectedOutput,
            Integer maxScore,
            String rubric,
            List<AssessmentTestCase> testCases
    ) {
        this.title = title;
        this.description = description;
        this.prompt = prompt;
        this.starterCode = starterCode;
        this.expectedOutput = expectedOutput;
        this.maxScore = maxScore;
        this.rubric = rubric;
        this.testCases = testCases == null ? new ArrayList<>() : testCases;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
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

    public Integer getMaxScore() {
        return maxScore;
    }

    public String getRubric() {
        return rubric;
    }

    public List<AssessmentTestCase> getTestCases() {
        return testCases;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public void setRubric(String rubric) {
        this.rubric = rubric;
    }

    public void setTestCases(List<AssessmentTestCase> testCases) {
        this.testCases = testCases == null ? new ArrayList<>() : testCases;
    }
}