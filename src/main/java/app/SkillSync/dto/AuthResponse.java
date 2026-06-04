package app.SkillSync.dto;

import app.SkillSync.model.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class AuthResponse {

    private String token;
    private String userId;
    private String fullName;
    private String email;
    private Role role;
    private String organizationId;
    private String profileImageUrl;
    private String organizationLogoUrl;
    private boolean requiresOrganizationSetup;

    public AuthResponse() {
    }

    public AuthResponse(String token, String userId, String fullName, String email, Role role) {
        this(token, userId, fullName, email, role, null, null, null, false);
    }

    public AuthResponse(
            String token,
            String userId,
            String fullName,
            String email,
            Role role,
            String organizationId,
            boolean requiresOrganizationSetup
    ) {
        this(token, userId, fullName, email, role, organizationId, null, null, requiresOrganizationSetup);
    }

    public AuthResponse(
            String token,
            String userId,
            String fullName,
            String email,
            Role role,
            String organizationId,
            String profileImageUrl,
            String organizationLogoUrl,
            boolean requiresOrganizationSetup
    ) {
        this.token = token;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.organizationId = organizationId;
        this.profileImageUrl = profileImageUrl;
        this.organizationLogoUrl = organizationLogoUrl;
        this.requiresOrganizationSetup = requiresOrganizationSetup;
    }

    @JsonIgnore
    public String getToken() {
        return token;
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

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getOrganizationLogoUrl() {
        return organizationLogoUrl;
    }

    public boolean isRequiresOrganizationSetup() {
        return requiresOrganizationSetup;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setOrganizationLogoUrl(String organizationLogoUrl) {
        this.organizationLogoUrl = organizationLogoUrl;
    }

    public void setRequiresOrganizationSetup(boolean requiresOrganizationSetup) {
        this.requiresOrganizationSetup = requiresOrganizationSetup;
    }
}
