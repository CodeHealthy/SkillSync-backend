package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_tokens")
public class EmailToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String candidateId;

    @Indexed
    private String organizationId;

    @Indexed
    private String email;

    private String recipientName;
    private Role invitedRole;

    @Indexed
    private String tokenHash;

    private AuthTokenType type;

    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    public EmailToken() {
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public Role getInvitedRole() {
        return invitedRole;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public AuthTokenType getType() {
        return type;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public void setInvitedRole(Role invitedRole) {
        this.invitedRole = invitedRole;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public void setType(AuthTokenType type) {
        this.type = type;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
