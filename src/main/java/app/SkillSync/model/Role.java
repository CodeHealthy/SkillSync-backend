package app.SkillSync.model;

public enum Role {
    SUPER_ADMIN,
    ORG_ADMIN,
    RECRUITER,
    HIRING_MANAGER,
    EVALUATOR,
    ADMIN,
    CANDIDATE;

    public boolean isPlatformAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isOrganizationAdmin() {
        return this == ORG_ADMIN || this == ADMIN;
    }

    public boolean isOrganizationStaff() {
        return isOrganizationAdmin() ||
                this == RECRUITER ||
                this == HIRING_MANAGER ||
                this == EVALUATOR;
    }

    public boolean canManageTeam() {
        return isOrganizationAdmin();
    }

    public boolean canManageBilling() {
        return isOrganizationAdmin();
    }

    public boolean canInviteCandidates() {
        return isOrganizationAdmin() || this == RECRUITER;
    }

    public boolean canCreateAssessments() {
        return isOrganizationAdmin() || this == RECRUITER || this == HIRING_MANAGER;
    }

    public boolean canReviewResults() {
        return isOrganizationAdmin() || this == HIRING_MANAGER || this == EVALUATOR;
    }
}
