package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "assessments")
public class Assessment {

    @Id
    private String id;

    private String title;
    private String description;
    private String roleTitle;
    private AssessmentStatus status = AssessmentStatus.PUBLISHED;
    private AssessmentType type;

    private ProgrammingLanguage language;

    private int maxScore;
    private Integer durationMinutes;

    private String prompt;
    private String starterCode;

    /**
     * Kept for backward compatibility with old single-output assessments.
     * New coding challenges should use testCases.
     */
    private String expectedOutput;

    private List<AssessmentTestCase> testCases = new ArrayList<>();
    private List<AssessmentSection> sections = new ArrayList<>();

    private Instant createdAt;
    private String organizationId;

    public Assessment() {
    }

    public String getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setId(String id) {
        this.id = id;
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
        this.status = status == null ? AssessmentStatus.PUBLISHED : status;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
