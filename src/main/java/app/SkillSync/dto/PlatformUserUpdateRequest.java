package app.SkillSync.dto;

import app.SkillSync.model.Role;

public class PlatformUserUpdateRequest {

    private Role role;
    private Boolean active;
    private String organizationId;

    public Role getRole() {
        return role;
    }

    public Boolean getActive() {
        return active;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
