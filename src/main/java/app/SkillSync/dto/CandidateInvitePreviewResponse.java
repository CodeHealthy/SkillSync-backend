package app.SkillSync.dto;

public class CandidateInvitePreviewResponse {

    private String candidateId;
    private String fullName;
    private String email;
    private String organizationName;
    private String organizationLogoUrl;

    public CandidateInvitePreviewResponse() {
    }

    public CandidateInvitePreviewResponse(
            String candidateId,
            String fullName,
            String email,
            String organizationName,
            String organizationLogoUrl
    ) {
        this.candidateId = candidateId;
        this.fullName = fullName;
        this.email = email;
        this.organizationName = organizationName;
        this.organizationLogoUrl = organizationLogoUrl;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationLogoUrl() {
        return organizationLogoUrl;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public void setOrganizationLogoUrl(String organizationLogoUrl) {
        this.organizationLogoUrl = organizationLogoUrl;
    }
}
