package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "organizations")
public class Organization {

    @Id
    private String id;

    private String name;

    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrganizationStatus getStatus() {
        return status == null ? OrganizationStatus.ACTIVE : status;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatus(OrganizationStatus status) {
        this.status = status == null ? OrganizationStatus.ACTIVE : status;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
