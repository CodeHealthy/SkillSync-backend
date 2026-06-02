package app.SkillSync.dto;

import app.SkillSync.model.OrganizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PlatformOrganizationUpdateRequest {

    @NotBlank(message = "Organization name is required.")
    @Size(max = 120, message = "Organization name must be 120 characters or fewer.")
    private String name;

    private OrganizationStatus status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public void setStatus(OrganizationStatus status) {
        this.status = status;
    }
}
