package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationSetupRequest {

    @NotBlank(message = "Organization name is required")
    @Size(max = 120, message = "Organization name must be 120 characters or fewer")
    private String organizationName;

    @Size(max = 3_000_000, message = "Profile image is too large")
    private String profileImageUrl;

    @Size(max = 3_000_000, message = "Organization image is too large")
    private String organizationLogoUrl;

    public OrganizationSetupRequest() {
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getOrganizationLogoUrl() {
        return organizationLogoUrl;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setOrganizationLogoUrl(String organizationLogoUrl) {
        this.organizationLogoUrl = organizationLogoUrl;
    }
}
