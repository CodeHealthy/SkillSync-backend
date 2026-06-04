package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Size(max = 3_000_000, message = "Profile image is too large")
    private String profileImageUrl;

    @Size(max = 3_000_000, message = "Organization image is too large")
    private String organizationLogoUrl;

    public UpdateProfileRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getOrganizationLogoUrl() {
        return organizationLogoUrl;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setOrganizationLogoUrl(String organizationLogoUrl) {
        this.organizationLogoUrl = organizationLogoUrl;
    }
}
