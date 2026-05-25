package app.SkillSync.dto;

import java.util.List;

public class PlatformAdminSummaryResponse {

    private List<PlatformOrganizationResponse> organizations;
    private List<PlatformUserResponse> users;

    public PlatformAdminSummaryResponse(
            List<PlatformOrganizationResponse> organizations,
            List<PlatformUserResponse> users
    ) {
        this.organizations = organizations;
        this.users = users;
    }

    public List<PlatformOrganizationResponse> getOrganizations() {
        return organizations;
    }

    public List<PlatformUserResponse> getUsers() {
        return users;
    }
}
