package app.SkillSync.service;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.AcceptCandidateInviteRequest;
import app.SkillSync.dto.AcceptTeamInviteRequest;
import app.SkillSync.dto.CandidateInvitePreviewResponse;
import app.SkillSync.dto.TeamInvitePreviewResponse;
import app.SkillSync.dto.ForgotPasswordRequest;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.OAuthExchangeRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.dto.ResendVerificationRequest;
import app.SkillSync.dto.ResetPasswordRequest;
import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.EmailToken;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.AuthCookieService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final String INVALID_LOGIN_MESSAGE = "Invalid email or password";

    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuthCookieService authCookieService;
    private final OrganizationRepository organizationRepository;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final OrganizationAccessService organizationAccessService;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            AuthCookieService authCookieService,
            CandidateRepository candidateRepository,
            OrganizationRepository organizationRepository,
            EmailTokenService emailTokenService,
            MailService mailService,
            AuditLogService auditLogService,
            OrganizationAccessService organizationAccessService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.authCookieService = authCookieService;
        this.candidateRepository = candidateRepository;
        this.organizationRepository = organizationRepository;
        this.emailTokenService = emailTokenService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.organizationAccessService = organizationAccessService;
    }

    public void register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.ORG_ADMIN;

        if (!role.isOrganizationAdmin()) {
            throw new IllegalArgumentException(
                    "Only organization admin employer accounts can be created through public signup."
            );
        }

        User user = new User();
        user.setFullName(normalizeRequiredText(request.getFullName(), "Full name is required"));
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        user.setActive(true);

        if (role.isOrganizationAdmin()) {
            String organizationName = normalizeRequiredText(
                    request.getOrganizationName(),
                    "Organization name is required for admin registration."
            );

            Organization organization = new Organization();
            organization.setName(organizationName);
            organization.setCreatedAt(Instant.now());

            Organization savedOrganization = organizationRepository.save(organization);
            user.setOrganizationId(savedOrganization.getId());
        }

        User savedUser = userRepository.save(user);

        auditLogService.recordForOrganization(
                savedUser,
                savedUser.getOrganizationId(),
                "ORG_REGISTERED",
                "ORGANIZATION",
                savedUser.getOrganizationId(),
                Map.of("email", savedUser.getEmail(), "role", savedUser.getRole())
        );

        sendVerificationEmail(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException exception) {
            auditLogService.recordUnauthenticated(
                    "LOGIN_FAILED",
                    "USER",
                    null,
                    normalizedEmail,
                    Map.of("reason", INVALID_LOGIN_MESSAGE)
            );
            throw new IllegalArgumentException(INVALID_LOGIN_MESSAGE);
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_LOGIN_MESSAGE));

        if (!user.isEmailVerifiedForLogin()) {
            auditLogService.record(
                    user,
                    "LOGIN_BLOCKED",
                    "USER",
                    user.getId(),
                    Map.of("reason", "EMAIL_UNVERIFIED")
            );
            throw new IllegalArgumentException("Please verify your email before logging in.");
        }

        if (!user.isActiveForLogin()) {
            auditLogService.record(
                    user,
                    "LOGIN_BLOCKED",
                    "USER",
                    user.getId(),
                    Map.of("reason", "ACCOUNT_DEACTIVATED")
            );
            throw new IllegalArgumentException("This account has been deactivated.");
        }

        try {
            organizationAccessService.requireActiveOrganizationForUser(user);
        } catch (IllegalArgumentException exception) {
            auditLogService.record(
                    user,
                    "LOGIN_BLOCKED",
                    "ORGANIZATION",
                    user.getOrganizationId(),
                    Map.of("reason", "ORGANIZATION_SUSPENDED")
            );
            throw exception;
        }

        auditLogService.record(
                user,
                "LOGIN_SUCCESS",
                "USER",
                user.getId(),
                Map.of("role", user.getRole())
        );

        return buildAuthResponse(user);
    }

    public void verifyEmail(String rawToken) {
        EmailToken token = emailTokenService.validateToken(
                rawToken,
                AuthTokenType.EMAIL_VERIFICATION
        );

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());

        User savedUser = userRepository.save(user);

        if (savedUser.getRole() == Role.CANDIDATE) {
            linkCandidateProfiles(savedUser);
        }

        emailTokenService.markUsed(token);
    }

    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account found for this email."));

        if (user.isEmailVerifiedForLogin()) {
            throw new IllegalArgumentException("This email is already verified.");
        }
        emailTokenService.enforceResendVerificationCooldown(user.getEmail());
        sendVerificationEmail(user);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmail(normalizedEmail)
                .filter(User::isEmailVerifiedForLogin)
                .ifPresent(this::sendPasswordResetEmail);
    }

    public void resetPassword(ResetPasswordRequest request) {
        EmailToken token = emailTokenService.validateToken(
                request.getToken(),
                AuthTokenType.PASSWORD_RESET
        );

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);
        emailTokenService.markUsed(token);
    }

    public AuthResponse exchangeOAuthCode(OAuthExchangeRequest request) {
        EmailToken token = emailTokenService.validateToken(
                request.getCode(),
                AuthTokenType.OAUTH_EXCHANGE
        );

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OAuth code."));

        if (!user.isEmailVerifiedForLogin()) {
            throw new IllegalArgumentException("Please verify your email before logging in.");
        }

        if (!user.isActiveForLogin()) {
            throw new IllegalArgumentException("This account has been deactivated.");
        }

        organizationAccessService.requireActiveOrganizationForUser(user);

        emailTokenService.markUsed(token);

        auditLogService.record(
                user,
                "OAUTH_EXCHANGE_COMPLETED",
                "USER",
                user.getId(),
                Map.of("role", user.getRole())
        );

        return buildAuthResponse(user);
    }

    public CandidateInvitePreviewResponse getCandidateInvite(String rawToken) {
        EmailToken token = emailTokenService.validateToken(
                rawToken,
                AuthTokenType.CANDIDATE_INVITE
        );

        Candidate candidate = findInvitedCandidate(token);
        Organization organization = organizationAccessService.requireActiveOrganization(
                candidate.getOrganizationId()
        );

        return new CandidateInvitePreviewResponse(
                candidate.getId(),
                candidate.getName(),
                candidate.getEmail(),
                organization.getName()
        );
    }

    public AuthResponse acceptCandidateInvite(AcceptCandidateInviteRequest request) {
        EmailToken token = emailTokenService.validateToken(
                request.getToken(),
                AuthTokenType.CANDIDATE_INVITE
        );

        Candidate candidate = findInvitedCandidate(token);
        organizationAccessService.requireActiveOrganization(candidate.getOrganizationId());
        String normalizedEmail = normalizeEmail(candidate.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account already exists for this invite email. Please log in instead."
            );
        }

        User user = new User();
        user.setFullName(normalizeRequiredText(request.getFullName(), "Full name is required"));
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CANDIDATE);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setActive(true);

        User savedUser = userRepository.save(user);

        candidate.setUserId(savedUser.getId());
        candidate.setStatus("REGISTERED");
        candidateRepository.save(candidate);

        emailTokenService.markUsed(token);

        auditLogService.recordForOrganization(
                savedUser,
                candidate.getOrganizationId(),
                "CANDIDATE_INVITE_ACCEPTED",
                "CANDIDATE",
                candidate.getId(),
                Map.of("email", savedUser.getEmail())
        );

        return buildAuthResponse(savedUser);
    }

    public TeamInvitePreviewResponse getTeamInvite(String rawToken, String fallbackName) {
        EmailToken token = emailTokenService.validateToken(
                rawToken,
                AuthTokenType.TEAM_MEMBER_INVITE
        );

        Organization organization = findInviteOrganization(token);
        organizationAccessService.requireActiveOrganization(organization.getId());

        return new TeamInvitePreviewResponse(
                normalizeOptionalText(
                        token.getRecipientName() != null
                                ? token.getRecipientName()
                                : fallbackName
                ),
                token.getEmail(),
                organization.getName(),
                token.getInvitedRole() != null ? token.getInvitedRole() : Role.RECRUITER
        );
    }

    public AuthResponse acceptTeamInvite(AcceptTeamInviteRequest request) {
        EmailToken token = emailTokenService.validateToken(
                request.getToken(),
                AuthTokenType.TEAM_MEMBER_INVITE
        );

        Organization organization = findInviteOrganization(token);
        organizationAccessService.requireActiveOrganization(organization.getId());
        String normalizedEmail = normalizeEmail(token.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account already exists for this invite email. Please log in instead."
            );
        }

        User user = new User();
        user.setFullName(normalizeRequiredText(request.getFullName(), "Full name is required"));
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(token.getInvitedRole() != null ? token.getInvitedRole() : Role.RECRUITER);
        user.setOrganizationId(organization.getId());
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setActive(true);

        User savedUser = userRepository.save(user);

        emailTokenService.markUsed(token);

        auditLogService.recordForOrganization(
                savedUser,
                savedUser.getOrganizationId(),
                "TEAM_INVITE_ACCEPTED",
                "USER",
                savedUser.getId(),
                Map.of("email", savedUser.getEmail(), "role", savedUser.getRole())
        );

        return buildAuthResponse(savedUser);
    }

    private void sendVerificationEmail(User user) {
        String rawToken = emailTokenService.createEmailVerificationToken(user);

        String verificationLink = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        mailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                verificationLink
        );
    }

    private void sendPasswordResetEmail(User user) {
        String rawToken = emailTokenService.createPasswordResetToken(user);

        String resetLink = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        mailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFullName(),
                resetLink
        );
    }

    private void linkCandidateProfiles(User savedUser) {
        List<Candidate> matchingCandidateProfiles =
                candidateRepository.findAllByEmailIgnoreCase(savedUser.getEmail());

        for (Candidate candidate : matchingCandidateProfiles) {
            candidate.setUserId(savedUser.getId());
            candidate.setStatus("REGISTERED");
            candidateRepository.save(candidate);
        }
    }

    private Candidate findInvitedCandidate(EmailToken token) {
        if (token.getCandidateId() == null || token.getCandidateId().isBlank()) {
            throw new IllegalArgumentException("Invalid or expired token.");
        }

        Candidate candidate = candidateRepository.findById(token.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate invite not found."));

        if (!candidate.getEmail().equalsIgnoreCase(token.getEmail())) {
            throw new IllegalArgumentException("Invalid or expired token.");
        }

        if (candidate.getUserId() != null || "REGISTERED".equals(candidate.getStatus())) {
            throw new IllegalArgumentException("This invite has already been accepted.");
        }

        return candidate;
    }

    private Organization findInviteOrganization(EmailToken token) {
        if (token.getOrganizationId() == null || token.getOrganizationId().isBlank()) {
            throw new IllegalArgumentException("Invalid or expired token.");
        }

        return organizationRepository.findById(token.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization invite not found."));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = authCookieService.createToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        return email.trim().toLowerCase();
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isBlank()) {
            return "";
        }

        return value.trim();
    }
}
