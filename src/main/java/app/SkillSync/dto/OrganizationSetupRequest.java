package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationSetupRequest {

    @NotBlank(message = "Organization name is required")
    @Size(max = 120, message = "Organization name must be 120 characters or fewer")
    private String organizationName;

    public OrganizationSetupRequest() {
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }
}
