package app.SkillSync.service;

import app.SkillSync.model.Organization;
import app.SkillSync.model.OrganizationStatus;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationAccessServiceTest {

    private OrganizationRepository organizationRepository;
    private OrganizationAccessService organizationAccessService;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        organizationAccessService = new OrganizationAccessService(organizationRepository);
    }

    @Test
    void requireActiveOrganizationAllowsActiveOrganization() {
        Organization organization = organization(OrganizationStatus.ACTIVE);

        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));

        assertDoesNotThrow(() -> organizationAccessService.requireActiveOrganization("org-1"));
    }

    @Test
    void requireActiveOrganizationBlocksSuspendedOrganization() {
        Organization organization = organization(OrganizationStatus.SUSPENDED);

        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> organizationAccessService.requireActiveOrganization("org-1")
        );

        assertEquals("This organization is suspended.", exception.getMessage());
    }

    @Test
    void requireActiveOrganizationForUserIgnoresCandidateUsers() {
        User user = new User();
        user.setRole(Role.CANDIDATE);

        assertDoesNotThrow(() -> organizationAccessService.requireActiveOrganizationForUser(user));
    }

    private Organization organization(OrganizationStatus status) {
        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setName("Hiring Co");
        organization.setStatus(status);
        return organization;
    }
}
