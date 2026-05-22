package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class SectionAttemptRequest {

    @NotBlank(message = "Section id is required")
    private String sectionId;

    public SectionAttemptRequest() {
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }
}
