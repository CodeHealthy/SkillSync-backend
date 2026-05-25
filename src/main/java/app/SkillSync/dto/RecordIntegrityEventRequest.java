package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class RecordIntegrityEventRequest {

    @NotBlank
    private String type;

    private String detail;
    private String sectionId;

    public RecordIntegrityEventRequest() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }
}
