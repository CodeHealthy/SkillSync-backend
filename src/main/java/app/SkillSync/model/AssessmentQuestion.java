package app.SkillSync.model;

import java.util.ArrayList;
import java.util.List;

public class AssessmentQuestion {

    private String id;
    private QuestionType type;
    private String title;
    private String prompt;
    private Integer points;
    private ProgrammingLanguage language;
    private String starterCode;
    private String expectedOutput;
    private String correctAnswer;
    private List<AssessmentQuestionOption> options = new ArrayList<>();
    private List<AssessmentTestCase> testCases = new ArrayList<>();

    public String getId() {
        return id;
    }

    public QuestionType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getPrompt() {
        return prompt;
    }

    public Integer getPoints() {
        return points;
    }

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public List<AssessmentQuestionOption> getOptions() {
        return options;
    }

    public List<AssessmentTestCase> getTestCases() {
        return testCases;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public void setLanguage(ProgrammingLanguage language) {
        this.language = language;
    }

    public void setStarterCode(String starterCode) {
        this.starterCode = starterCode;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public void setOptions(List<AssessmentQuestionOption> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public void setTestCases(List<AssessmentTestCase> testCases) {
        this.testCases = testCases == null ? new ArrayList<>() : testCases;
    }
}
