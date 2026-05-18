package app.SkillSync.service;

import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private CandidateRepository candidateRepository;
    private OrganizationRepository organizationRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private EmailTokenService emailTokenService;
    private MailService mailService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);
        emailTokenService = mock(EmailTokenService.class);
        mailService = mock(MailService.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                candidateRepository,
                organizationRepository,
                emailTokenService,
                mailService
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
        verify(jwtService, never()).generateToken(any(User.class));
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
    void registerCandidateCreatesUnverifiedUserAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Candidate Demo");
        request.setEmail("candidate@skillsync.com");
        request.setPassword("password123");
        request.setRole(Role.CANDIDATE);

        User savedUser = new User();
        savedUser.setId("candidate-user-123");
        savedUser.setFullName("Candidate Demo");
        savedUser.setEmail("candidate@skillsync.com");
        savedUser.setRole(Role.CANDIDATE);
        savedUser.setEmailVerified(false);

        when(userRepository.existsByEmail("candidate@skillsync.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(emailTokenService.createEmailVerificationToken(savedUser))
                .thenReturn("verification-token");

        authService.register(request);

        verify(userRepository).save(any(User.class));
        verify(emailTokenService).createEmailVerificationToken(savedUser);
        verify(mailService).sendVerificationEmail(
                eq("candidate@skillsync.com"),
                eq("Candidate Demo"),
                contains("/verify-email?token=verification-token")
        );

        verify(candidateRepository, never()).findAllByEmailIgnoreCase(anyString());
        verify(candidateRepository, never()).save(any(Candidate.class));
        verify(organizationRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any(User.class));
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

        app.SkillSync.model.EmailToken token = new app.SkillSync.model.EmailToken();
        token.setUserId("candidate-user-123");
        token.setEmail("candidate@skillsync.com");

        when(emailTokenService.validateToken(
                eq("raw-verification-token"),
                eq(app.SkillSync.model.AuthTokenType.EMAIL_VERIFICATION)
        )).thenReturn(token);

        when(userRepository.findById("candidate-user-123")).thenReturn(java.util.Optional.of(savedUser));
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
}