package app.SkillSync.model;

public class TestCaseExecutionResult {

    private String name;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private String error;
    private Integer exitCode;
    private Boolean timedOut;
    private Boolean passed;
    private Boolean hidden;
    private Integer points;
    private Integer awardedPoints;

    public TestCaseExecutionResult() {
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

    public String getActualOutput() {
        return actualOutput;
    }

    public String getError() {
        return error;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public Boolean getTimedOut() {
        return timedOut;
    }

    public Boolean getPassed() {
        return passed;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public Integer getPoints() {
        return points;
    }

    public Integer getAwardedPoints() {
        return awardedPoints;
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

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public void setTimedOut(Boolean timedOut) {
        this.timedOut = timedOut;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public void setAwardedPoints(Integer awardedPoints) {
        this.awardedPoints = awardedPoints;
    }
}