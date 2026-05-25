package app.SkillSync.dto;

import app.SkillSync.model.Role;

import java.time.Instant;

public class TeamMemberResponse {

    private String userId;
    private String fullName;
    private String email;
    private Role role;
    private Instant createdAt;
    private Boolean active;
    private Instant deactivatedAt;

    public TeamMemberResponse() {
    }

    public TeamMemberResponse(
            String userId,
            String fullName,
            String email,
            Role role,
            Instant createdAt,
            Boolean active,
            Instant deactivatedAt
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.active = active;
        this.deactivatedAt = deactivatedAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Boolean getActive() {
        return active;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }
}
