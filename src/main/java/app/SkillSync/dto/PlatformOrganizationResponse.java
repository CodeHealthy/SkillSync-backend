package app.SkillSync.dto;

import app.SkillSync.model.OrganizationStatus;

import java.time.Instant;

public class PlatformOrganizationResponse {

    private String organizationId;
    private String name;
    private OrganizationStatus status;
    private Instant createdAt;
    private long userCount;

    public PlatformOrganizationResponse(
            String organizationId,
            String name,
            OrganizationStatus status,
            Instant createdAt,
            long userCount
    ) {
        this.organizationId = organizationId;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.userCount = userCount;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getUserCount() {
        return userCount;
    }
}
