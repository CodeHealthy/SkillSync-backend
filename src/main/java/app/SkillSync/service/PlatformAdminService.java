package app.SkillSync.service;

import app.SkillSync.dto.PlatformAdminSummaryResponse;
import app.SkillSync.dto.PlatformOrganizationResponse;
import app.SkillSync.dto.PlatformUserResponse;
import app.SkillSync.model.Organization;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PlatformAdminService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PlatformAdminService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public PlatformAdminSummaryResponse getSummary(Authentication authentication) {
        User superAdmin = requireSuperAdmin(authentication);

        List<User> users = userRepository.findAll();

        List<PlatformOrganizationResponse> organizations = organizationRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(Organization::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(organization -> toOrganizationResponse(organization, users))
                .toList();

        List<PlatformUserResponse> userResponses = users
                .stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toUserResponse)
                .toList();

        auditLogService.record(
                superAdmin,
                "PLATFORM_SUMMARY_VIEWED",
                "PLATFORM",
                "summary",
                Map.of(
                        "organizationCount", organizations.size(),
                        "userCount", userResponses.size()
                )
        );

        return new PlatformAdminSummaryResponse(organizations, userResponses);
    }

    private User requireSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (user.getRole() == null || !user.getRole().isPlatformAdmin()) {
            throw new IllegalArgumentException("Only platform super admins can access this area.");
        }

        return user;
    }

    private PlatformOrganizationResponse toOrganizationResponse(
            Organization organization,
            List<User> users
    ) {
        long userCount = users.stream()
                .filter(user -> organization.getId() != null &&
                        organization.getId().equals(user.getOrganizationId()))
                .count();

        return new PlatformOrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCreatedAt(),
                userCount
        );
    }

    private PlatformUserResponse toUserResponse(User user) {
        return new PlatformUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getOrganizationId(),
                user.isActiveForLogin(),
                user.getCreatedAt()
        );
    }
}
