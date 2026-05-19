package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AiAssessmentRequest {

    @NotBlank
    @Size(max = 120)
    private String roleTitle;

    @NotBlank
    @Size(max = 120)
    private String skillTopic;

    @NotBlank
    @Pattern(regexp = "Easy|Medium|Hard")
    private String difficulty;

    @NotBlank
    @Pattern(regexp = "QUIZ|CODING_CHALLENGE")
    private String assessmentType;

    @Size(max = 30)
    private String language;

    @Size(max = 500)
    private String context;

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getSkillTopic() {
        return skillTopic;
    }

    public void setSkillTopic(String skillTopic) {
        this.skillTopic = skillTopic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(String assessmentType) {
        this.assessmentType = assessmentType;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}