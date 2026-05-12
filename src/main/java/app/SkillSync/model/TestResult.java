package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "test_results")
public class TestResult {

    @Id
    private String id;
    private String testName;
    private int score;
    private String status;  // Passed, Failed, Pending, etc.
    private String submissionTime; // Time when the test was completed
    private String answers; // Can be a JSON string or text (based on how you store answers)

    // Constructors
    public TestResult(String testName, int score, String status, String submissionTime, String answers) {
        this.testName = testName;
        this.score = score;
        this.status = status;
        this.submissionTime = submissionTime;
        this.answers = answers;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmissionTime() {
        return submissionTime;
    }

    public void setSubmissionTime(String submissionTime) {
        this.submissionTime = submissionTime;
    }

    public String getAnswers() {
        return answers;
    }

    public void setAnswers(String answers) {
        this.answers = answers;
    }
}