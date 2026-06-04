package app.SkillSync.dto;

import app.SkillSync.model.Role;

public class TeamInvitePreviewResponse {

    private String fullName;
    private String email;
    private String organizationName;
    private String organizationLogoUrl;
    private Role role;

    public TeamInvitePreviewResponse() {
    }

    public TeamInvitePreviewResponse(
            String fullName,
            String email,
            String organizationName,
            String organizationLogoUrl,
            Role role
    ) {
        this.fullName = fullName;
        this.email = email;
        this.organizationName = organizationName;
        this.organizationLogoUrl = organizationLogoUrl;
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationLogoUrl() {
        return organizationLogoUrl;
    }

    public Role getRole() {
        return role;
    }
}
