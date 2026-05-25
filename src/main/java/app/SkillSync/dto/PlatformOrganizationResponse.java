package app.SkillSync.dto;

import java.time.Instant;

public class PlatformOrganizationResponse {

    private String organizationId;
    private String name;
    private Instant createdAt;
    private long userCount;

    public PlatformOrganizationResponse(
            String organizationId,
            String name,
            Instant createdAt,
            long userCount
    ) {
        this.organizationId = organizationId;
        this.name = name;
        this.createdAt = createdAt;
        this.userCount = userCount;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getUserCount() {
        return userCount;
    }
}
