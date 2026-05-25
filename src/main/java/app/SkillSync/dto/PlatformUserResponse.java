package app.SkillSync.dto;

import app.SkillSync.model.Role;

import java.time.Instant;

public class PlatformUserResponse {

    private String userId;
    private String fullName;
    private String email;
    private Role role;
    private String organizationId;
    private Boolean active;
    private Instant createdAt;

    public PlatformUserResponse(
            String userId,
            String fullName,
            String email,
            Role role,
            String organizationId,
            Boolean active,
            Instant createdAt
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.organizationId = organizationId;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public Boolean getActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
