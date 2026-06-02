package app.SkillSync.service;

import app.SkillSync.model.Organization;
import app.SkillSync.model.OrganizationStatus;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

@Service
public class OrganizationAccessService {

    private final OrganizationRepository organizationRepository;

    public OrganizationAccessService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization requireActiveOrganization(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Organization id is required.");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found."));

        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new IllegalArgumentException("This organization is suspended.");
        }

        return organization;
    }

    public void requireActiveOrganizationForUser(User user) {
        if (user == null || user.getRole() == null || !user.getRole().isOrganizationStaff()) {
            return;
        }

        requireActiveOrganization(user.getOrganizationId());
    }
}
