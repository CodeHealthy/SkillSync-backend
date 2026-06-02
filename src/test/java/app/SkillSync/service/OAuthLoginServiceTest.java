package app.SkillSync.service;

import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.EmailToken;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginServiceTest {

    private UserRepository userRepository;
    private CandidateRepository candidateRepository;
    private OrganizationAccessService organizationAccessService;
    private PasswordEncoder passwordEncoder;
    private EmailTokenService emailTokenService;
    private OAuthLoginService oAuthLoginService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        organizationAccessService = mock(OrganizationAccessService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        emailTokenService = mock(EmailTokenService.class);

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-oauth-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(user.getId() == null ? "user-1" : user.getId());
            return user;
        });

        oAuthLoginService = new OAuthLoginService(
                userRepository,
                candidateRepository,
                organizationAccessService,
                passwordEncoder,
                jwtService,
                emailTokenService
        );
    }

    @Test
    void publicGoogleSignupCreatesRecruiterSideOrganizationAdminPendingSetup() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());

        User user = oAuthLoginService.processGoogleLogin(
                googleUser("owner@example.com", "Owner User"),
                "public",
                null
        );

        assertEquals(Role.ORG_ADMIN, user.getRole());
        assertNull(user.getOrganizationId());
        assertEquals("owner@example.com", user.getEmail());
    }

    @Test
    void candidateInviteGoogleSignupCreatesCandidateAndLinksInviteProfile() {
        Candidate candidate = candidate("candidate-1", "org-1", "candidate@example.com");
        EmailToken token = candidateInviteToken("candidate-1", "candidate@example.com");

        when(emailTokenService.validateToken("candidate-token", AuthTokenType.CANDIDATE_INVITE))
                .thenReturn(token);
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(organizationAccessService.requireActiveOrganization("org-1"))
                .thenReturn(organization("org-1"));
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.empty());

        User user = oAuthLoginService.processGoogleLogin(
                googleUser("candidate@example.com", "Candidate User"),
                "candidate-invite",
                "candidate-token"
        );

        assertEquals(Role.CANDIDATE, user.getRole());
        assertEquals("user-1", candidate.getUserId());
        assertEquals("REGISTERED", candidate.getStatus());
        verify(candidateRepository).save(candidate);
        verify(emailTokenService).markUsed(token);
    }

    @Test
    void teamInviteGoogleSignupCreatesInvitedOrganizationRole() {
        EmailToken token = teamInviteToken(
                "org-1",
                "reviewer@example.com",
                Role.EVALUATOR
        );

        when(emailTokenService.validateToken("team-token", AuthTokenType.TEAM_MEMBER_INVITE))
                .thenReturn(token);
        when(organizationAccessService.requireActiveOrganization("org-1"))
                .thenReturn(organization("org-1"));
        when(userRepository.findByEmail("reviewer@example.com")).thenReturn(Optional.empty());

        User user = oAuthLoginService.processGoogleLogin(
                googleUser("reviewer@example.com", "Reviewer User"),
                "team-invite",
                "team-token"
        );

        assertEquals(Role.EVALUATOR, user.getRole());
        assertEquals("org-1", user.getOrganizationId());
        verify(emailTokenService).markUsed(token);
    }

    private OAuth2User googleUser(String email, String name) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "email", email,
                        "name", name,
                        "email_verified", true
                ),
                "email"
        );
    }

    private Candidate candidate(String id, String organizationId, String email) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setOrganizationId(organizationId);
        candidate.setEmail(email);
        return candidate;
    }

    private Organization organization(String id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName("Test Organization");
        return organization;
    }

    private EmailToken candidateInviteToken(String candidateId, String email) {
        EmailToken token = new EmailToken();
        token.setCandidateId(candidateId);
        token.setEmail(email);
        token.setType(AuthTokenType.CANDIDATE_INVITE);
        return token;
    }

    private EmailToken teamInviteToken(String organizationId, String email, Role role) {
        EmailToken token = new EmailToken();
        token.setOrganizationId(organizationId);
        token.setEmail(email);
        token.setInvitedRole(role);
        token.setType(AuthTokenType.TEAM_MEMBER_INVITE);
        return token;
    }
}
