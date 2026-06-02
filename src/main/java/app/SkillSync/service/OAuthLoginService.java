package app.SkillSync.service;

import app.SkillSync.model.Candidate;
import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.EmailToken;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OAuthLoginService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final OrganizationAccessService organizationAccessService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailTokenService emailTokenService;

    public OAuthLoginService(
            UserRepository userRepository,
            CandidateRepository candidateRepository,
            OrganizationAccessService organizationAccessService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailTokenService emailTokenService
    ) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.organizationAccessService = organizationAccessService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailTokenService = emailTokenService;
    }

    public User processGoogleLogin(OAuth2User oauthUser) {
        return processGoogleLogin(oauthUser, null, null);
    }

    public User processGoogleLogin(
            OAuth2User oauthUser,
            String flow,
            String inviteToken
    ) {
        String email = extractEmail(oauthUser);
        String fullName = extractFullName(oauthUser, email);
        boolean googleEmailVerified = extractEmailVerified(oauthUser);

        if ("candidate-invite".equals(flow)) {
            return processCandidateInviteLogin(
                    inviteToken,
                    email,
                    fullName,
                    googleEmailVerified
            );
        }

        if ("team-invite".equals(flow)) {
            return processTeamInviteLogin(
                    inviteToken,
                    email,
                    fullName,
                    googleEmailVerified
            );
        }

        User user = userRepository.findByEmail(email)
                .map(existingUser ->
                        processExistingUser(existingUser, fullName, googleEmailVerified)
                )
                .orElseGet(() -> createOrganizationAdminUser(email, fullName, googleEmailVerified));

        if (user.getRole() == Role.CANDIDATE) {
            linkCandidateProfiles(user);
        }

        return user;
    }

    public String generateToken(User user) {
        return jwtService.generateToken(user);
    }

    public String createExchangeCode(User user) {
        return emailTokenService.createOAuthExchangeToken(user);
    }

    private User processExistingUser(
            User user,
            String fullName,
            boolean googleEmailVerified
    ) {
        boolean changed = false;

        if ((user.getFullName() == null || user.getFullName().isBlank())
                && fullName != null
                && !fullName.isBlank()) {
            user.setFullName(fullName);
            changed = true;
        }

        if (googleEmailVerified && !user.isEmailVerifiedForLogin()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            changed = true;
        }

        if (user.getRole() != null && user.getRole().isOrganizationStaff()) {
            if (!user.isEmailVerifiedForLogin() && !googleEmailVerified) {
                throw new IllegalArgumentException(
                        "Google could not confirm this organization account email as verified."
                );
            }

            return changed ? userRepository.save(user) : user;
        }

        if (user.getRole() == Role.CANDIDATE) {
            return changed ? userRepository.save(user) : user;
        }

        throw new IllegalArgumentException("Unsupported user role for Google login.");
    }

    private User createCandidateUser(
            String email,
            String fullName,
            boolean googleEmailVerified
    ) {
        if (!googleEmailVerified) {
            throw new IllegalArgumentException(
                    "Google account email must be verified before signing in."
            );
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OAUTH2_GOOGLE_" + System.nanoTime()));
        user.setRole(Role.CANDIDATE);
        user.setOrganizationId(null);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setActive(true);

        return userRepository.save(user);
    }

    private User createOrganizationAdminUser(
            String email,
            String fullName,
            boolean googleEmailVerified
    ) {
        requireVerifiedGoogleEmail(googleEmailVerified);

        return createVerifiedGoogleUser(
                email,
                fullName,
                Role.ORG_ADMIN,
                null
        );
    }

    private User createOrganizationStaffUser(
            String email,
            String fullName,
            Role role,
            String organizationId,
            boolean googleEmailVerified
    ) {
        requireVerifiedGoogleEmail(googleEmailVerified);

        if (role == null || !role.isOrganizationStaff()) {
            throw new IllegalArgumentException("Invite role is not supported for Google signup.");
        }

        return createVerifiedGoogleUser(email, fullName, role, organizationId);
    }

    private User createVerifiedGoogleUser(
            String email,
            String fullName,
            Role role,
            String organizationId
    ) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OAUTH2_GOOGLE_" + System.nanoTime()));
        user.setRole(role);
        user.setOrganizationId(organizationId);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setActive(true);

        return userRepository.save(user);
    }

    private User processCandidateInviteLogin(
            String rawToken,
            String googleEmail,
            String fullName,
            boolean googleEmailVerified
    ) {
        EmailToken token = emailTokenService.validateToken(
                rawToken,
                AuthTokenType.CANDIDATE_INVITE
        );

        Candidate candidate = candidateRepository.findById(token.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate invite is no longer valid."));
        organizationAccessService.requireActiveOrganization(candidate.getOrganizationId());

        if (!normalizeEmail(candidate.getEmail()).equals(googleEmail)) {
            throw new IllegalArgumentException(
                    "Google account email must match the candidate invite email."
            );
        }

        if (candidate.getUserId() != null && !candidate.getUserId().isBlank()) {
            throw new IllegalArgumentException("Candidate invite has already been accepted.");
        }

        User user = userRepository.findByEmail(googleEmail)
                .map(existingUser ->
                        processExistingCandidateInviteUser(
                                existingUser,
                                fullName,
                                googleEmailVerified
                        )
                )
                .orElseGet(() -> createCandidateUser(googleEmail, fullName, googleEmailVerified));

        linkCandidateProfile(user, candidate);
        emailTokenService.markUsed(token);

        return user;
    }

    private User processTeamInviteLogin(
            String rawToken,
            String googleEmail,
            String fullName,
            boolean googleEmailVerified
    ) {
        EmailToken token = emailTokenService.validateToken(
                rawToken,
                AuthTokenType.TEAM_MEMBER_INVITE
        );
        Organization organization =
                organizationAccessService.requireActiveOrganization(token.getOrganizationId());
        Role invitedRole = token.getInvitedRole() == null
                ? Role.RECRUITER
                : token.getInvitedRole();

        if (!normalizeEmail(token.getEmail()).equals(googleEmail)) {
            throw new IllegalArgumentException(
                    "Google account email must match the team invite email."
            );
        }

        User user = userRepository.findByEmail(googleEmail)
                .map(existingUser ->
                        processExistingTeamInviteUser(
                                existingUser,
                                fullName,
                                googleEmailVerified,
                                invitedRole,
                                organization.getId()
                        )
                )
                .orElseGet(() ->
                        createOrganizationStaffUser(
                                googleEmail,
                                fullName,
                                invitedRole,
                                organization.getId(),
                                googleEmailVerified
                        )
                );

        emailTokenService.markUsed(token);

        return user;
    }

    private User processExistingCandidateInviteUser(
            User user,
            String fullName,
            boolean googleEmailVerified
    ) {
        if (user.getRole() != Role.CANDIDATE) {
            throw new IllegalArgumentException(
                    "An existing non-candidate account cannot accept this candidate invite."
            );
        }

        return processExistingUser(user, fullName, googleEmailVerified);
    }

    private User processExistingTeamInviteUser(
            User user,
            String fullName,
            boolean googleEmailVerified,
            Role invitedRole,
            String organizationId
    ) {
        if (user.getRole() != invitedRole
                || user.getOrganizationId() == null
                || !user.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException(
                    "An existing account for this email does not match this team invite."
            );
        }

        return processExistingUser(user, fullName, googleEmailVerified);
    }

    private void requireVerifiedGoogleEmail(boolean googleEmailVerified) {
        if (!googleEmailVerified) {
            throw new IllegalArgumentException(
                    "Google account email must be verified before signing in."
            );
        }
    }

    private void linkCandidateProfiles(User user) {
        List<Candidate> matchingCandidateProfiles =
                candidateRepository.findAllByEmailIgnoreCase(user.getEmail());

        for (Candidate candidate : matchingCandidateProfiles) {
            linkCandidateProfile(user, candidate);
        }
    }

    private void linkCandidateProfile(User user, Candidate candidate) {
        candidate.setUserId(user.getId());
        candidate.setStatus("REGISTERED");
        candidateRepository.save(candidate);
    }

    private String extractEmail(OAuth2User oauthUser) {
        Object emailAttribute = oauthUser.getAttribute("email");

        if (emailAttribute == null || emailAttribute.toString().trim().isBlank()) {
            throw new IllegalArgumentException("Google account did not provide an email address.");
        }

        return emailAttribute.toString().trim().toLowerCase();
    }

    private String extractFullName(OAuth2User oauthUser, String email) {
        Object nameAttribute = oauthUser.getAttribute("name");

        if (nameAttribute != null && !nameAttribute.toString().trim().isBlank()) {
            return nameAttribute.toString().trim();
        }

        return email;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isBlank()) {
            return "";
        }

        return email.trim().toLowerCase();
    }

    private boolean extractEmailVerified(OAuth2User oauthUser) {
        Object emailVerifiedAttribute = oauthUser.getAttribute("email_verified");

        if (emailVerifiedAttribute instanceof Boolean verified) {
            return verified;
        }

        if (emailVerifiedAttribute != null) {
            return Boolean.parseBoolean(emailVerifiedAttribute.toString());
        }

        return false;
    }
}
