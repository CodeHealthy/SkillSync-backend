package app.SkillSync.model;

public class SubscriptionFeatures {

    private Long activeAssessments;
    private Long candidateInvites;
    private Boolean aiGeneration;
    private Boolean proctoring;
    private Boolean branding;
    private Long teamMembers;

    public Long getActiveAssessments() {
        return activeAssessments;
    }

    public void setActiveAssessments(Long activeAssessments) {
        this.activeAssessments = activeAssessments;
    }

    public Long getCandidateInvites() {
        return candidateInvites;
    }

    public void setCandidateInvites(Long candidateInvites) {
        this.candidateInvites = candidateInvites;
    }

    public Boolean getAiGeneration() {
        return aiGeneration;
    }

    public void setAiGeneration(Boolean aiGeneration) {
        this.aiGeneration = aiGeneration;
    }

    public Boolean getProctoring() {
        return proctoring;
    }

    public void setProctoring(Boolean proctoring) {
        this.proctoring = proctoring;
    }

    public Boolean getBranding() {
        return branding;
    }

    public void setBranding(Boolean branding) {
        this.branding = branding;
    }

    public Long getTeamMembers() {
        return teamMembers;
    }

    public void setTeamMembers(Long teamMembers) {
        this.teamMembers = teamMembers;
    }
}