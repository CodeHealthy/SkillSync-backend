package app.SkillSync.service;

import app.SkillSync.dto.AcceptCandidateInviteRequest;
import app.SkillSync.dto.AcceptTeamInviteRequest;
import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.CandidateInvitePreviewResponse;
import app.SkillSync.dto.OAuthExchangeRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.dto.TeamInvitePreviewResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private CandidateRepository candidateRepository;
    private OrganizationRepository organizationRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private AuthCookieService authCookieService;
    private EmailTokenService emailTokenService;
    private MailService mailService;
    private AuditLogService auditLogService;
    private OrganizationAccessService organizationAccessService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        authCookieService = mock(AuthCookieService.class);
        emailTokenService = mock(EmailTokenService.class);
        mailService = mock(MailService.class);
        auditLogService = mock(AuditLogService.class);
        organizationAccessService = mock(OrganizationAccessService.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                authCookieService,
                candidateRepository,
                organizationRepository,
                emailTokenService,
                mailService,
                auditLogService,
                organizationAccessService
        );

        ReflectionTestUtils.setField(
                authService,
                "frontendBaseUrl",
                "http://localhost:3000"
        );
    }

    @Test
    void registerAdminCreatesOrganizationAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Admin Demo");
        request.setEmail("admin@skillsync.com");
        request.setPassword("password123");
        request.setRole(Role.ADMIN);
        request.setOrganizationName("SkillSync Demo Org");

        Organization savedOrganization = new Organization();
        savedOrganization.setId("org-123");
        savedOrganization.setName("SkillSync Demo Org");

        User savedUser = new User();
        savedUser.setId("user-123");
        savedUser.setFullName("Admin Demo");
        savedUser.setEmail("admin@skillsync.com");
        savedUser.setRole(Role.ADMIN);
        savedUser.setOrganizationId("org-123");
        savedUser.setEmailVerified(false);

        when(userRepository.existsByEmail("admin@skillsync.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrganization);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(emailTokenService.createEmailVerificationToken(savedUser))
                .thenReturn("verification-token");

        authService.register(request);

        verify(organizationRepository).save(any(Organization.class));
        verify(userRepository).save(any(User.class));
        verify(emailTokenService).createEmailVerificationToken(savedUser);
        verify(mailService).sendVerificationEmail(
                eq("admin@skillsync.com"),
                eq("Admin Demo"),
                contains("/verify-email?token=verification-token")
        );
        verify(candidateRepository, never()).findAllByEmailIgnoreCase(anyString());
        verify(authCookieService, never()).createToken(any(User.class));
    }

    @Test
    void registerAdminWithoutOrganizationNameThrowsError() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Admin Demo");
        request.setEmail("admin@skillsync.com");
        request.setPassword("password123");
        request.setRole(Role.ADMIN);
        request.setOrganizationName("");

        when(userRepository.existsByEmail("admin@skillsync.com")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        assertEquals("Organization name is required for admin registration.", exception.getMessage());

        verify(organizationRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(emailTokenService, never()).createEmailVerificationToken(any(User.class));
        verify(mailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerCandidateRejectsPublicCandidateSignup() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Candidate Demo");
        request.setEmail("candidate@skillsync.com");
        request.setPassword("password123");
        request.setRole(Role.CANDIDATE);

        when(userRepository.existsByEmail("candidate@skillsync.com")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        assertEquals(
                "Only organization admin employer accounts can be created through public signup.",
                exception.getMessage()
        );

        verify(candidateRepository, never()).findAllByEmailIgnoreCase(anyString());
        verify(candidateRepository, never()).save(any(Candidate.class));
        verify(organizationRepository, never()).save(any());
        verify(userRepository, never()).save(any(User.class));
        verify(authCookieService, never()).createToken(any(User.class));
    }

    @Test
    void registerRejectsPublicSuperAdminSignup() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Platform Owner");
        request.setEmail("owner@skillsync.com");
        request.setPassword("Password123!");
        request.setRole(Role.SUPER_ADMIN);
        request.setOrganizationName("SkillSync");

        when(userRepository.existsByEmail("owner@skillsync.com")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        assertEquals(
                "Only organization admin employer accounts can be created through public signup.",
                exception.getMessage()
        );

        verify(candidateRepository, never()).findAllByEmailIgnoreCase(anyString());
        verify(candidateRepository, never()).save(any(Candidate.class));
        verify(organizationRepository, never()).save(any());
        verify(userRepository, never()).save(any(User.class));
        verify(authCookieService, never()).createToken(any(User.class));
    }

    @Test
    void registerWithExistingEmailThrowsError() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Existing User");
        request.setEmail("existing@skillsync.com");
        request.setPassword("password123");
        request.setRole(Role.CANDIDATE);

        when(userRepository.existsByEmail("existing@skillsync.com")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        assertEquals("Email is already registered", exception.getMessage());

        verify(userRepository, never()).save(any());
        verify(candidateRepository, never()).save(any());
        verify(organizationRepository, never()).save(any());
        verify(emailTokenService, never()).createEmailVerificationToken(any(User.class));
        verify(mailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void verifyEmailLinksExistingInvitedCandidateProfiles() {
        User savedUser = new User();
        savedUser.setId("candidate-user-123");
        savedUser.setFullName("Candidate Demo");
        savedUser.setEmail("candidate@skillsync.com");
        savedUser.setRole(Role.CANDIDATE);
        savedUser.setEmailVerified(false);

        Candidate invitedProfileOne = new Candidate();
        invitedProfileOne.setId("candidate-profile-1");
        invitedProfileOne.setEmail("candidate@skillsync.com");
        invitedProfileOne.setStatus("INVITED");

        Candidate invitedProfileTwo = new Candidate();
        invitedProfileTwo.setId("candidate-profile-2");
        invitedProfileTwo.setEmail("candidate@skillsync.com");
        invitedProfileTwo.setStatus("INVITED");

        EmailToken token = new EmailToken();
        token.setUserId("candidate-user-123");
        token.setEmail("candidate@skillsync.com");

        when(emailTokenService.validateToken(
                eq("raw-verification-token"),
                eq(AuthTokenType.EMAIL_VERIFICATION)
        )).thenReturn(token);

        when(userRepository.findById("candidate-user-123"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(candidateRepository.findAllByEmailIgnoreCase("candidate@skillsync.com"))
                .thenReturn(List.of(invitedProfileOne, invitedProfileTwo));

        authService.verifyEmail("raw-verification-token");

        assertEquals(Boolean.TRUE, savedUser.getEmailVerified());
        assertNotNull(savedUser.getEmailVerifiedAt());

        assertEquals("candidate-user-123", invitedProfileOne.getUserId());
        assertEquals("REGISTERED", invitedProfileOne.getStatus());

        assertEquals("candidate-user-123", invitedProfileTwo.getUserId());
        assertEquals("REGISTERED", invitedProfileTwo.getStatus());

        verify(candidateRepository, times(2)).save(any(Candidate.class));
        verify(emailTokenService).markUsed(token);
    }

    @Test
    void getCandidateInviteReturnsCandidateAndOrganizationPreview() {
        EmailToken token = new EmailToken();
        token.setCandidateId("candidate-profile-1");
        token.setEmail("candidate@skillsync.com");

        Candidate candidate = new Candidate();
        candidate.setId("candidate-profile-1");
        candidate.setName("Candidate Demo");
        candidate.setEmail("candidate@skillsync.com");
        candidate.setOrganizationId("org-1");
        candidate.setStatus("INVITED");

        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setName("SkillSync Demo Org");

        when(emailTokenService.validateToken(
                eq("raw-invite-token"),
                eq(AuthTokenType.CANDIDATE_INVITE)
        )).thenReturn(token);
        when(candidateRepository.findById("candidate-profile-1"))
                .thenReturn(Optional.of(candidate));
        when(organizationAccessService.requireActiveOrganization("org-1"))
                .thenReturn(organization);

        CandidateInvitePreviewResponse response =
                authService.getCandidateInvite("raw-invite-token");

        assertEquals("candidate-profile-1", response.getCandidateId());
        assertEquals("Candidate Demo", response.getFullName());
        assertEquals("candidate@skillsync.com", response.getEmail());
        assertEquals("SkillSync Demo Org", response.getOrganizationName());
    }

    @Test
    void acceptCandidateInviteCreatesVerifiedCandidateUserAndLinksProfile() {
        AcceptCandidateInviteRequest request = new AcceptCandidateInviteRequest();
        request.setToken("raw-invite-token");
        request.setFullName("Candidate Demo");
        request.setPassword("Password123!");

        EmailToken token = new EmailToken();
        token.setCandidateId("candidate-profile-1");
        token.setEmail("candidate@skillsync.com");

        Candidate candidate = new Candidate();
        candidate.setId("candidate-profile-1");
        candidate.setName("Candidate Demo");
        candidate.setEmail("candidate@skillsync.com");
        candidate.setOrganizationId("org-1");
        candidate.setStatus("INVITED");

        User savedUser = new User();
        savedUser.setId("candidate-user-123");
        savedUser.setFullName("Candidate Demo");
        savedUser.setEmail("candidate@skillsync.com");
        savedUser.setRole(Role.CANDIDATE);
        savedUser.setEmailVerified(true);

        when(emailTokenService.validateToken(
                eq("raw-invite-token"),
                eq(AuthTokenType.CANDIDATE_INVITE)
        )).thenReturn(token);
        when(candidateRepository.findById("candidate-profile-1"))
                .thenReturn(Optional.of(candidate));
        when(userRepository.existsByEmail("candidate@skillsync.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(authCookieService.createToken(savedUser)).thenReturn("jwt-token");

        AuthResponse response = authService.acceptCandidateInvite(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("candidate-user-123", response.getUserId());
        assertEquals(Role.CANDIDATE, response.getRole());
        assertEquals("candidate-user-123", candidate.getUserId());
        assertEquals("REGISTERED", candidate.getStatus());

        verify(candidateRepository).save(candidate);
        verify(emailTokenService).markUsed(token);
    }

    @Test
    void exchangeOAuthCodeReturnsAuthResponseAndMarksExchangeTokenUsed() {
        OAuthExchangeRequest request = new OAuthExchangeRequest();
        request.setCode("oauth-exchange-code");

        EmailToken token = new EmailToken();
        token.setUserId("user-123");
        token.setEmail("admin@skillsync.com");

        User user = new User();
        user.setId("user-123");
        user.setFullName("Admin Demo");
        user.setEmail("admin@skillsync.com");
        user.setRole(Role.ADMIN);
        user.setEmailVerified(true);
        user.setActive(true);

        when(emailTokenService.validateToken(
                eq("oauth-exchange-code"),
                eq(AuthTokenType.OAUTH_EXCHANGE)
        )).thenReturn(token);
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(authCookieService.createToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.exchangeOAuthCode(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("user-123", response.getUserId());
        assertEquals("admin@skillsync.com", response.getEmail());
        assertEquals(Role.ADMIN, response.getRole());

        verify(emailTokenService).markUsed(token);
        verify(auditLogService).record(
                eq(user),
                eq("OAUTH_EXCHANGE_COMPLETED"),
                eq("USER"),
                eq("user-123"),
                anyMap()
        );
    }

    @Test
    void acceptCandidateInviteRejectsAlreadyAcceptedCandidate() {
        AcceptCandidateInviteRequest request = new AcceptCandidateInviteRequest();
        request.setToken("raw-invite-token");
        request.setFullName("Candidate Demo");
        request.setPassword("Password123!");

        EmailToken token = new EmailToken();
        token.setCandidateId("candidate-profile-1");
        token.setEmail("candidate@skillsync.com");

        Candidate candidate = new Candidate();
        candidate.setId("candidate-profile-1");
        candidate.setEmail("candidate@skillsync.com");
        candidate.setUserId("candidate-user-123");
        candidate.setStatus("REGISTERED");

        when(emailTokenService.validateToken(
                eq("raw-invite-token"),
                eq(AuthTokenType.CANDIDATE_INVITE)
        )).thenReturn(token);
        when(candidateRepository.findById("candidate-profile-1"))
                .thenReturn(Optional.of(candidate));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.acceptCandidateInvite(request)
        );

        assertEquals("This invite has already been accepted.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(emailTokenService, never()).markUsed(any(EmailToken.class));
    }

    @Test
    void getTeamInviteReturnsOrganizationPreview() {
        EmailToken token = new EmailToken();
        token.setOrganizationId("org-1");
        token.setEmail("teammate@skillsync.com");

        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setName("SkillSync Demo Org");

        when(emailTokenService.validateToken(
                eq("raw-team-token"),
                eq(AuthTokenType.TEAM_MEMBER_INVITE)
        )).thenReturn(token);
        when(organizationRepository.findById("org-1"))
                .thenReturn(Optional.of(organization));

        TeamInvitePreviewResponse response =
                authService.getTeamInvite("raw-team-token", "Team Member");

        assertEquals("Team Member", response.getFullName());
        assertEquals("teammate@skillsync.com", response.getEmail());
        assertEquals("SkillSync Demo Org", response.getOrganizationName());
    }

    @Test
    void acceptTeamInviteCreatesVerifiedAdminInOrganization() {
        AcceptTeamInviteRequest request = new AcceptTeamInviteRequest();
        request.setToken("raw-team-token");
        request.setFullName("Team Member");
        request.setPassword("Password123!");

        EmailToken token = new EmailToken();
        token.setOrganizationId("org-1");
        token.setEmail("teammate@skillsync.com");

        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setName("SkillSync Demo Org");

        User savedUser = new User();
        savedUser.setId("team-user-123");
        savedUser.setFullName("Team Member");
        savedUser.setEmail("teammate@skillsync.com");
        savedUser.setRole(Role.ADMIN);
        savedUser.setOrganizationId("org-1");
        savedUser.setEmailVerified(true);

        when(emailTokenService.validateToken(
                eq("raw-team-token"),
                eq(AuthTokenType.TEAM_MEMBER_INVITE)
        )).thenReturn(token);
        when(organizationRepository.findById("org-1"))
                .thenReturn(Optional.of(organization));
        when(userRepository.existsByEmail("teammate@skillsync.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(authCookieService.createToken(savedUser)).thenReturn("jwt-token");

        AuthResponse response = authService.acceptTeamInvite(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("team-user-123", response.getUserId());
        assertEquals(Role.ADMIN, response.getRole());

        verify(emailTokenService).markUsed(token);
    }
}
