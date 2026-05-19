package app.SkillSync.dto;

import java.util.ArrayList;
import java.util.List;

public class AssignmentRunResult {

    private String language;
    private Integer totalTests;
    private Integer passedTests;
    private Integer totalPoints;
    private Integer awardedPoints;
    private List<TestCaseRunSummary> testResults = new ArrayList<>();

    public AssignmentRunResult() {
    }

    public String getLanguage() {
        return language;
    }

    public Integer getTotalTests() {
        return totalTests;
    }

    public Integer getPassedTests() {
        return passedTests;
    }

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public Integer getAwardedPoints() {
        return awardedPoints;
    }

    public List<TestCaseRunSummary> getTestResults() {
        return testResults;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setTotalTests(Integer totalTests) {
        this.totalTests = totalTests;
    }

    public void setPassedTests(Integer passedTests) {
        this.passedTests = passedTests;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

    public void setAwardedPoints(Integer awardedPoints) {
        this.awardedPoints = awardedPoints;
    }

    public void setTestResults(List<TestCaseRunSummary> testResults) {
        this.testResults = testResults;
    }
}