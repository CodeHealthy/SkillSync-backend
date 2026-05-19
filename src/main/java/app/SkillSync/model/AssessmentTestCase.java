package app.SkillSync.model;

public class AssessmentTestCase {

    private String name;
    private String input;
    private String expectedOutput;
    private Boolean hidden;
    private Integer points;

    public AssessmentTestCase() {
    }

    public AssessmentTestCase(
            String name,
            String input,
            String expectedOutput,
            Boolean hidden,
            Integer points
    ) {
        this.name = name;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.hidden = hidden;
        this.points = points;
    }

    public String getName() {
        return name;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public Integer getPoints() {
        return points;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }
}