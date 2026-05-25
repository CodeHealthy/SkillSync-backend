package app.SkillSync.service;

import app.SkillSync.dto.PlatformAdminSummaryResponse;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAdminServiceTest {

    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private AuditLogService auditLogService;
    private PlatformAdminService platformAdminService;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogService = mock(AuditLogService.class);
        platformAdminService = new PlatformAdminService(
                organizationRepository,
                userRepository,
                auditLogService
        );
    }

    @Test
    void getSummaryAllowsSuperAdminAndReturnsPlatformCounts() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );

        Organization organization = organization("org-1", "SkillSync Demo Org");
        User orgAdmin = user(
                "admin-1",
                "Org Admin",
                "admin@skillsync.com",
                Role.ORG_ADMIN,
                "org-1"
        );
        User recruiter = user(
                "recruiter-1",
                "Recruiter",
                "recruiter@skillsync.com",
                Role.RECRUITER,
                "org-1"
        );

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findAll())
                .thenReturn(List.of(superAdmin, orgAdmin, recruiter));
        when(organizationRepository.findAll())
                .thenReturn(List.of(organization));

        PlatformAdminSummaryResponse response = platformAdminService.getSummary(
                authentication("owner@skillsync.com")
        );

        assertEquals(1, response.getOrganizations().size());
        assertEquals("SkillSync Demo Org", response.getOrganizations().get(0).getName());
        assertEquals(2, response.getOrganizations().get(0).getUserCount());
        assertEquals(3, response.getUsers().size());
    }

    @Test
    void getSummaryRejectsOrganizationAdmin() {
        User orgAdmin = user(
                "admin-1",
                "Org Admin",
                "admin@skillsync.com",
                Role.ORG_ADMIN,
                "org-1"
        );

        when(userRepository.findByEmail("admin@skillsync.com"))
                .thenReturn(Optional.of(orgAdmin));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformAdminService.getSummary(authentication("admin@skillsync.com"))
        );

        assertEquals(
                "Only platform super admins can access this area.",
                exception.getMessage()
        );
    }

    private Authentication authentication(String email) {
        return new UsernamePasswordAuthenticationToken(email, null);
    }

    private User user(
            String id,
            String fullName,
            String email,
            Role role,
            String organizationId
    ) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setOrganizationId(organizationId);
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private Organization organization(String id, String name) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return organization;
    }
}
