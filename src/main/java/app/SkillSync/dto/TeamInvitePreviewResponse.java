package app.SkillSync.dto;

import app.SkillSync.model.Role;

public class TeamInvitePreviewResponse {

    private String fullName;
    private String email;
    private String organizationName;
    private Role role;

    public TeamInvitePreviewResponse() {
    }

    public TeamInvitePreviewResponse(String fullName, String email, String organizationName, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.organizationName = organizationName;
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

    public Role getRole() {
        return role;
    }
}
