package app.SkillSync.dto;

import app.SkillSync.model.Role;

import java.time.Instant;

public class TeamInviteResponse {

    private String inviteId;
    private String fullName;
    private String email;
    private Instant createdAt;
    private Instant expiresAt;
    private String status;
    private Role role;

    public TeamInviteResponse() {
    }

    public TeamInviteResponse(
            String inviteId,
            String fullName,
            String email,
            Instant createdAt,
            Instant expiresAt,
            String status,
            Role role
    ) {
        this.inviteId = inviteId;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.role = role;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public Role getRole() {
        return role;
    }
}
