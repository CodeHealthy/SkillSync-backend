package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "candidates")
public class Candidate {

    @Id
    private String id;

    private String name;

    @Indexed
    private String email;

    private List<TestResult> testResults = new ArrayList<>();

    private Instant createdAt;

    public Candidate() {
    }

    public Candidate(String name, String email) {
        this.name = name;
        this.email = email;
        this.testResults = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setTestResults(List<TestResult> testResults) {
        this.testResults = testResults;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}