package app.SkillSync.service;

import app.SkillSync.dto.InviteTeamMemberRequest;
import app.SkillSync.dto.TeamInviteResponse;
import app.SkillSync.dto.TeamMemberResponse;
import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.EmailToken;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.EmailTokenRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationTeamService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;
    private final BillingService billingService;
    private final AuditLogService auditLogService;
    private final OrganizationAccessService organizationAccessService;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public OrganizationTeamService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            EmailTokenRepository emailTokenRepository,
            EmailTokenService emailTokenService,
            MailService mailService,
            BillingService billingService,
            AuditLogService auditLogService,
            OrganizationAccessService organizationAccessService
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.emailTokenService = emailTokenService;
        this.mailService = mailService;
        this.billingService = billingService;
        this.auditLogService = auditLogService;
        this.organizationAccessService = organizationAccessService;
    }

    public List<TeamMemberResponse> listTeamMembers(Authentication authentication) {
        User currentUser = requireAdminUser(authentication);
        String organizationId = requireOrganizationId(currentUser);

        return userRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(user -> user.getRole() != null && user.getRole().isOrganizationStaff())
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTeamMemberResponse)
                .toList();
    }

    public List<TeamInviteResponse> listPendingInvites(Authentication authentication) {
        User currentUser = requireAdminUser(authentication);
        String organizationId = requireOrganizationId(currentUser);

        return emailTokenRepository
                .findByOrganizationIdAndTypeOrderByCreatedAtDesc(
                        organizationId,
                        AuthTokenType.TEAM_MEMBER_INVITE
                )
                .stream()
                .filter(token -> token.getUsedAt() == null)
                .map(this::toTeamInviteResponse)
                .toList();
    }

    public Map<String, String> inviteTeamMember(
            Authentication authentication,
            InviteTeamMemberRequest request
    ) {
        User currentUser = requireAdminUser(authentication);
        String organizationId = requireOrganizationId(currentUser);
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmail(normalizedEmail).ifPresent(existingUser -> {
            if (organizationId.equals(existingUser.getOrganizationId()) &&
                    existingUser.getRole() != null &&
                    existingUser.getRole().isOrganizationStaff()) {
                throw new IllegalArgumentException("This team member already belongs to your organization.");
            }

            throw new IllegalArgumentException("This email is already registered.");
        });

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found."));

        Role invitedRole = normalizeTeamInviteRole(request.getRole());
        billingService.ensureCanInviteTeamMember(organizationId);

        String rawToken = emailTokenService.createTeamMemberInviteToken(
                normalizedEmail,
                organizationId,
                request.getFullName().trim(),
                invitedRole
        );

        mailService.sendTeamMemberInviteEmail(
                normalizedEmail,
                request.getFullName().trim(),
                organization.getName(),
                buildTeamInviteLink(rawToken, request.getFullName().trim())
        );

        auditLogService.record(
                currentUser,
                "TEAM_INVITE_SENT",
                "USER_INVITE",
                normalizedEmail,
                Map.of("email", normalizedEmail, "role", invitedRole)
        );

        return Map.of("message", "Team invite sent.");
    }

    public Map<String, String> resendTeamInvite(
            Authentication authentication,
            String inviteId
    ) {
        User currentUser = requireAdminUser(authentication);
        String organizationId = requireOrganizationId(currentUser);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found."));
        EmailToken existingInvite = requirePendingTeamInvite(inviteId, organizationId);

        existingInvite.setUsedAt(Instant.now());
        emailTokenRepository.save(existingInvite);

        String fullName = normalizeInviteName(existingInvite);
        String rawToken = emailTokenService.createTeamMemberInviteToken(
                existingInvite.getEmail(),
                organizationId,
                fullName,
                existingInvite.getInvitedRole() != null
                        ? existingInvite.getInvitedRole()
                        : Role.RECRUITER
        );

        mailService.sendTeamMemberInviteEmail(
                existingInvite.getEmail(),
                fullName,
                organization.getName(),
                buildTeamInviteLink(rawToken, fullName)
        );

        auditLogService.record(
                currentUser,
                "TEAM_INVITE_RESENT",
                "USER_INVITE",
                existingInvite.getId(),
                Map.of("email", existingInvite.getEmail())
        );

        return Map.of("message", "Team invite resent.");
    }

    public Map<String, String> revokeTeamInvite(
            Authentication authentication,
            String inviteId
    ) {
        User currentUser = requireAdminUser(authentication);
        String organizationId = requireOrganizationId(currentUser);
        EmailToken invite = requirePendingTeamInvite(inviteId, organizationId);

        invite.setUsedAt(Instant.now());
        emailTokenRepository.save(invite);

        auditLogService.record(
                currentUser,
                "TEAM_INVITE_REVOKED",
                "USER_INVITE",
                invite.getId(),
                Map.of("email", invite.getEmail())
        );

        return Map.of("message", "Team invite revoked.");
    }

    public Map<String, String> deactivateTeamMember(
            Authentication authentication,
            String userId
    ) {
        User currentUser = requireAdminUser(authentication);
        String organizationId = requireOrganizationId(currentUser);

        if (currentUser.getId() != null && currentUser.getId().equals(userId)) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }

        User teamMember = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found."));

        if (!organizationId.equals(teamMember.getOrganizationId()) ||
                teamMember.getRole() == null ||
                !teamMember.getRole().isOrganizationStaff()) {
            throw new IllegalArgumentException("Team member does not belong to your organization.");
        }

        if (!teamMember.isActiveForLogin()) {
            return Map.of("message", "Team member is already deactivated.");
        }

        long activeAdmins = userRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(user -> user.getRole() != null && user.getRole().isOrganizationAdmin())
                .filter(User::isActiveForLogin)
                .count();

        if (activeAdmins <= 1) {
            throw new IllegalArgumentException("You cannot deactivate the last active admin.");
        }

        teamMember.setActive(false);
        teamMember.setDeactivatedAt(Instant.now());
        userRepository.save(teamMember);

        auditLogService.record(
                currentUser,
                "TEAM_MEMBER_DEACTIVATED",
                "USER",
                teamMember.getId(),
                Map.of("email", teamMember.getEmail(), "role", teamMember.getRole())
        );

        return Map.of("message", "Team member deactivated.");
    }

    private User requireAdminUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (user.getRole() == null || !user.getRole().canManageTeam()) {
            throw new IllegalArgumentException("Only organization admins can manage team members.");
        }

        return user;
    }

    private String requireOrganizationId(User user) {
        if (user.getOrganizationId() == null || user.getOrganizationId().isBlank()) {
            throw new IllegalArgumentException("Admin is not linked to an organization.");
        }

        organizationAccessService.requireActiveOrganization(user.getOrganizationId());
        return user.getOrganizationId();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        return email.trim().toLowerCase();
    }

    private EmailToken requirePendingTeamInvite(String inviteId, String organizationId) {
        EmailToken invite = emailTokenRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Team invite not found."));

        if (invite.getType() != AuthTokenType.TEAM_MEMBER_INVITE ||
                !organizationId.equals(invite.getOrganizationId())) {
            throw new IllegalArgumentException("Team invite not found.");
        }

        if (invite.getUsedAt() != null) {
            throw new IllegalArgumentException("This invite is no longer pending.");
        }

        return invite;
    }

    private Role normalizeTeamInviteRole(Role requestedRole) {
        Role role = requestedRole != null ? requestedRole : Role.RECRUITER;

        if (!role.isOrganizationStaff() || role == Role.ADMIN) {
            throw new IllegalArgumentException("Invalid team role.");
        }

        return role;
    }

    private String buildTeamInviteLink(String rawToken, String fullName) {
        return UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/accept-team-invite")
                .queryParam("token", rawToken)
                .queryParam("name", fullName)
                .build()
                .encode()
                .toUriString();
    }

    private TeamInviteResponse toTeamInviteResponse(EmailToken invite) {
        String status = invite.getExpiresAt() != null &&
                invite.getExpiresAt().isBefore(Instant.now())
                ? "EXPIRED"
                : "PENDING";

        return new TeamInviteResponse(
                invite.getId(),
                normalizeInviteName(invite),
                invite.getEmail(),
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                status,
                invite.getInvitedRole() != null ? invite.getInvitedRole() : Role.RECRUITER
        );
    }

    private String normalizeInviteName(EmailToken invite) {
        if (invite.getRecipientName() != null && !invite.getRecipientName().isBlank()) {
            return invite.getRecipientName().trim();
        }

        return invite.getEmail();
    }

    private TeamMemberResponse toTeamMemberResponse(User user) {
        return new TeamMemberResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.isActiveForLogin(),
                user.getDeactivatedAt()
        );
    }
}
