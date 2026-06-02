package app.SkillSync.service;

import app.SkillSync.dto.PlatformAdminSummaryResponse;
import app.SkillSync.dto.PlatformOrganizationResponse;
import app.SkillSync.dto.PlatformOrganizationUpdateRequest;
import app.SkillSync.dto.PlatformSubscriptionPlanRequest;
import app.SkillSync.dto.PlatformUserResponse;
import app.SkillSync.dto.PlatformUserUpdateRequest;
import app.SkillSync.dto.SubscriptionPlanResponse;
import app.SkillSync.model.Organization;
import app.SkillSync.model.OrganizationStatus;
import app.SkillSync.model.Role;
import app.SkillSync.model.SubscriptionPlan;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.SubscriptionPlanRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAdminServiceTest {

    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private AuditLogService auditLogService;
    private PlatformAdminService platformAdminService;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        auditLogService = mock(AuditLogService.class);
        platformAdminService = new PlatformAdminService(
                organizationRepository,
                userRepository,
                subscriptionPlanRepository,
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

    @Test
    void createSubscriptionPlanAllowsSuperAdminAndStoresFeatureMap() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        PlatformSubscriptionPlanRequest request = planRequest("Growth");

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(subscriptionPlanRepository.findFirstByCodeIgnoreCase("growth"))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.save(org.mockito.ArgumentMatchers.any(SubscriptionPlan.class)))
                .thenAnswer(invocation -> {
                    SubscriptionPlan saved = invocation.getArgument(0);
                    saved.setId("plan-1");
                    return saved;
                });

        SubscriptionPlanResponse response = platformAdminService.createSubscriptionPlan(
                authentication("owner@skillsync.com"),
                request
        );

        assertEquals("growth", response.getCode());
        assertEquals(25, response.getFeatures().get("activeAssessments"));
        assertEquals(true, response.getFeatures().get("aiGeneration"));
    }

    @Test
    void updateSubscriptionPlanRejectsDuplicatePlanCode() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        SubscriptionPlan existing = subscriptionPlan("plan-1", "growth");
        SubscriptionPlan duplicate = subscriptionPlan("plan-2", "growth");

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(subscriptionPlanRepository.findById("plan-1"))
                .thenReturn(Optional.of(existing));
        when(subscriptionPlanRepository.findFirstByCodeIgnoreCase("growth"))
                .thenReturn(Optional.of(duplicate));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformAdminService.updateSubscriptionPlan(
                        authentication("owner@skillsync.com"),
                        "plan-1",
                        planRequest("Growth")
                )
        );

        assertEquals("Subscription plan code already exists.", exception.getMessage());
    }

    @Test
    void deactivateSubscriptionPlanPreventsDeactivatingFreePlan() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        SubscriptionPlan freePlan = subscriptionPlan("plan-free", "free");
        freePlan.setIsFree(true);

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(subscriptionPlanRepository.findById("plan-free"))
                .thenReturn(Optional.of(freePlan));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformAdminService.deactivateSubscriptionPlan(
                        authentication("owner@skillsync.com"),
                        "plan-free"
                )
        );

        assertEquals("The free plan cannot be deactivated.", exception.getMessage());
    }

    @Test
    void updateUserAllowsSuperAdminToChangeRoleAndActiveStatus() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        User targetUser = user(
                "user-1",
                "Recruiter",
                "recruiter@skillsync.com",
                Role.RECRUITER,
                "org-1"
        );
        PlatformUserUpdateRequest request = new PlatformUserUpdateRequest();
        request.setRole(Role.HIRING_MANAGER);
        request.setActive(false);

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(targetUser));
        when(organizationRepository.findById("org-1"))
                .thenReturn(Optional.of(organization("org-1", "Existing Org")));
        when(userRepository.save(targetUser)).thenReturn(targetUser);

        PlatformUserResponse response = platformAdminService.updateUser(
                authentication("owner@skillsync.com"),
                "user-1",
                request
        );

        assertEquals(Role.HIRING_MANAGER, response.getRole());
        assertEquals(false, response.getActive());
    }

    @Test
    void updateUserAllowsSuperAdminToMoveOrganizationStaffToAnotherOrganization() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        User targetUser = user(
                "user-1",
                "Recruiter",
                "recruiter@skillsync.com",
                Role.RECRUITER,
                "org-1"
        );
        PlatformUserUpdateRequest request = new PlatformUserUpdateRequest();
        request.setRole(Role.HIRING_MANAGER);
        request.setActive(true);
        request.setOrganizationId("org-2");

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(targetUser));
        when(organizationRepository.findById("org-2"))
                .thenReturn(Optional.of(organization("org-2", "Next Org")));
        when(userRepository.save(targetUser)).thenReturn(targetUser);

        PlatformUserResponse response = platformAdminService.updateUser(
                authentication("owner@skillsync.com"),
                "user-1",
                request
        );

        assertEquals(Role.HIRING_MANAGER, response.getRole());
        assertEquals("org-2", response.getOrganizationId());
    }

    @Test
    void updateUserRejectsOrganizationStaffRoleWithoutOrganization() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        User targetUser = user(
                "user-1",
                "Candidate",
                "candidate@skillsync.com",
                Role.CANDIDATE,
                null
        );
        PlatformUserUpdateRequest request = new PlatformUserUpdateRequest();
        request.setRole(Role.RECRUITER);
        request.setActive(true);

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(targetUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformAdminService.updateUser(
                        authentication("owner@skillsync.com"),
                        "user-1",
                        request
                )
        );

        assertEquals("Organization is required for organization staff roles.", exception.getMessage());
    }

    @Test
    void updateUserPreventsRemovingLastActiveSuperAdmin() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        PlatformUserUpdateRequest request = new PlatformUserUpdateRequest();
        request.setRole(Role.ORG_ADMIN);
        request.setActive(true);

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findById("owner-1"))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findAll()).thenReturn(List.of(superAdmin));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformAdminService.updateUser(
                        authentication("owner@skillsync.com"),
                        "owner-1",
                        request
                )
        );

        assertEquals("At least one active super admin is required.", exception.getMessage());
    }

    @Test
    void updateOrganizationAllowsSuperAdminToRenameOrganization() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        Organization organization = organization("org-1", "Old Org");
        User orgAdmin = user(
                "admin-1",
                "Org Admin",
                "admin@skillsync.com",
                Role.ORG_ADMIN,
                "org-1"
        );
        PlatformOrganizationUpdateRequest request = new PlatformOrganizationUpdateRequest();
        request.setName("New Org");

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(organizationRepository.findById("org-1"))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.findFirstByNameIgnoreCase("New Org"))
                .thenReturn(Optional.empty());
        when(organizationRepository.save(organization)).thenReturn(organization);
        when(userRepository.findAll()).thenReturn(List.of(superAdmin, orgAdmin));

        PlatformOrganizationResponse response = platformAdminService.updateOrganization(
                authentication("owner@skillsync.com"),
                "org-1",
                request
        );

        assertEquals("New Org", response.getName());
        assertEquals(1, response.getUserCount());
    }

    @Test
    void updateOrganizationRejectsDuplicateOrganizationName() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        Organization organization = organization("org-1", "Old Org");
        Organization duplicate = organization("org-2", "New Org");
        PlatformOrganizationUpdateRequest request = new PlatformOrganizationUpdateRequest();
        request.setName("New Org");

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(organizationRepository.findById("org-1"))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.findFirstByNameIgnoreCase("New Org"))
                .thenReturn(Optional.of(duplicate));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformAdminService.updateOrganization(
                        authentication("owner@skillsync.com"),
                        "org-1",
                        request
                )
        );

        assertEquals("Organization name already exists.", exception.getMessage());
    }

    @Test
    void updateOrganizationAllowsSuperAdminToSuspendOrganization() {
        User superAdmin = user(
                "owner-1",
                "Platform Owner",
                "owner@skillsync.com",
                Role.SUPER_ADMIN,
                null
        );
        Organization organization = organization("org-1", "Hiring Co");
        PlatformOrganizationUpdateRequest request = new PlatformOrganizationUpdateRequest();
        request.setName("Hiring Co");
        request.setStatus(OrganizationStatus.SUSPENDED);

        when(userRepository.findByEmail("owner@skillsync.com"))
                .thenReturn(Optional.of(superAdmin));
        when(organizationRepository.findById("org-1"))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.findFirstByNameIgnoreCase("Hiring Co"))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.save(organization)).thenReturn(organization);
        when(userRepository.findAll()).thenReturn(List.of(superAdmin));

        PlatformOrganizationResponse response = platformAdminService.updateOrganization(
                authentication("owner@skillsync.com"),
                "org-1",
                request
        );

        assertEquals(OrganizationStatus.SUSPENDED, response.getStatus());
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

    private PlatformSubscriptionPlanRequest planRequest(String name) {
        PlatformSubscriptionPlanRequest request = new PlatformSubscriptionPlanRequest();
        request.setCode(name);
        request.setName(name);
        request.setCurrency("aed");
        request.setBillingCycle("month");
        request.setFeatures(Map.of(
                "activeAssessments", 25,
                "candidateInvites", 500,
                "aiGeneration", true
        ));
        request.setHighlights(List.of("25 active assessments", "500 invites"));
        request.setActive(true);
        request.setIsFree(false);
        return request;
    }

    private SubscriptionPlan subscriptionPlan(String id, String code) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(code);
        plan.setFeatures(Map.of());
        return plan;
    }
}
