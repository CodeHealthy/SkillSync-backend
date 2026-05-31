package app.SkillSync.service;

import app.SkillSync.dto.CreateCandidateRequest;
import app.SkillSync.dto.SubmitTestResultRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateServiceTest {

    private CandidateRepository candidateRepository;
    private UserRepository userRepository;
    private BillingService billingService;
    private EmailTokenService emailTokenService;
    private MailService mailService;
    private AuditLogService auditLogService;
    private CandidateService candidateService;

    @BeforeEach
    void setUp() {
        candidateRepository = mock(CandidateRepository.class);
        userRepository = mock(UserRepository.class);
        billingService = mock(BillingService.class);
        emailTokenService = mock(EmailTokenService.class);
        mailService = mock(MailService.class);
        auditLogService = mock(AuditLogService.class);
        candidateService = new CandidateService(
                candidateRepository,
                userRepository,
                billingService,
                emailTokenService,
                mailService,
                auditLogService
        );
        ReflectionTestUtils.setField(
                candidateService,
                "frontendBaseUrl",
                "http://localhost:3000"
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createCandidate_whenAdminHasOrganization_createsInvitedCandidateInsideAdminOrganization() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.existsByOrganizationIdAndEmailIgnoreCase("org-1", "candidate@example.com"))
                .thenReturn(false);
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.empty());
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailTokenService.createCandidateInviteToken(any(Candidate.class))).thenReturn("invite-token");

        CreateCandidateRequest request = createCandidateRequest(
                " Candidate Demo ",
                " Candidate@Example.com "
        );

        Candidate savedCandidate = candidateService.createCandidate(request);

        assertEquals("Candidate Demo", savedCandidate.getName());
        assertEquals("candidate@example.com", savedCandidate.getEmail());
        assertEquals("org-1", savedCandidate.getOrganizationId());
        assertEquals("admin-1", savedCandidate.getCreatedByAdminId());
        assertEquals("INVITED", savedCandidate.getStatus());
        assertNull(savedCandidate.getUserId());
        assertNotNull(savedCandidate.getCreatedAt());

        verify(candidateRepository).save(any(Candidate.class));
        verify(mailService).sendCandidateInviteEmail(
                eq("candidate@example.com"),
                eq("Candidate Demo"),
                contains("/accept-invite?token=invite-token")
        );
    }

    @Test
    void createCandidate_whenAdminHasNoOrganization_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", null);

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        CreateCandidateRequest request = createCandidateRequest(
                "Candidate Demo",
                "candidate@example.com"
        );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> candidateService.createCandidate(request)
        );

        assertEquals("Admin is not linked to an organization.", exception.getMessage());

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    @Test
    void createCandidate_whenCandidateAlreadyExistsInSameOrganization_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.existsByOrganizationIdAndEmailIgnoreCase("org-1", "candidate@example.com"))
                .thenReturn(true);

        CreateCandidateRequest request = createCandidateRequest(
                "Candidate Demo",
                "candidate@example.com"
        );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> candidateService.createCandidate(request)
        );

        assertEquals("Candidate already exists in your organization.", exception.getMessage());

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    @Test
    void createCandidate_whenCandidateUserAlreadyExists_linksCandidateProfileAndMarksRegistered() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        User existingCandidateUser = new User();
        existingCandidateUser.setId("user-candidate-1");
        existingCandidateUser.setEmail("candidate@example.com");
        existingCandidateUser.setRole(Role.CANDIDATE);

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.existsByOrganizationIdAndEmailIgnoreCase("org-1", "candidate@example.com"))
                .thenReturn(false);
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(existingCandidateUser));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCandidateRequest request = createCandidateRequest(
                "Candidate Demo",
                "candidate@example.com"
        );

        Candidate savedCandidate = candidateService.createCandidate(request);

        assertEquals("user-candidate-1", savedCandidate.getUserId());
        assertEquals("REGISTERED", savedCandidate.getStatus());
        assertEquals("org-1", savedCandidate.getOrganizationId());
        verify(emailTokenService, never()).createCandidateInviteToken(any(Candidate.class));
        verify(mailService, never()).sendCandidateInviteEmail(anyString(), anyString(), anyString());
    }

    @Test
    void createCandidate_whenExistingUserIsAdmin_doesNotLinkCandidateProfile() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        User existingAdminUser = new User();
        existingAdminUser.setId("other-admin-1");
        existingAdminUser.setEmail("candidate@example.com");
        existingAdminUser.setRole(Role.ADMIN);

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.existsByOrganizationIdAndEmailIgnoreCase("org-1", "candidate@example.com"))
                .thenReturn(false);
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(existingAdminUser));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailTokenService.createCandidateInviteToken(any(Candidate.class))).thenReturn("invite-token");

        CreateCandidateRequest request = createCandidateRequest(
                "Candidate Demo",
                "candidate@example.com"
        );

        Candidate savedCandidate = candidateService.createCandidate(request);

        assertNull(savedCandidate.getUserId());
        assertEquals("INVITED", savedCandidate.getStatus());
        verify(mailService).sendCandidateInviteEmail(
                eq("candidate@example.com"),
                eq("Candidate Demo"),
                contains("/accept-invite?token=invite-token")
        );
    }

    @Test
    void getAllCandidates_returnsOnlyCandidatesFromAdminOrganization() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        Candidate candidateOne = candidate("candidate-1", "Candidate One", "one@example.com", "org-1");
        Candidate candidateTwo = candidate("candidate-2", "Candidate Two", "two@example.com", "org-1");

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.findByOrganizationId("org-1"))
                .thenReturn(List.of(candidateOne, candidateTwo));

        List<Candidate> candidates = candidateService.getAllCandidates();

        assertEquals(2, candidates.size());
        assertEquals("candidate-1", candidates.get(0).getId());
        assertEquals("candidate-2", candidates.get(1).getId());

        verify(candidateRepository).findByOrganizationId("org-1");
    }

    @Test
    void searchCandidatesByName_returnsOnlyCandidatesFromAdminOrganization() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        Candidate candidateOne = candidate("candidate-1", "Candidate One", "one@example.com", "org-1");

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.findByOrganizationIdAndNameContainingIgnoreCase("org-1", "Candidate"))
                .thenReturn(List.of(candidateOne));

        List<Candidate> candidates = candidateService.searchCandidatesByName("Candidate");

        assertEquals(1, candidates.size());
        assertEquals("candidate-1", candidates.get(0).getId());

        verify(candidateRepository).findByOrganizationIdAndNameContainingIgnoreCase("org-1", "Candidate");
        verify(candidateRepository, never()).findByNameContainingIgnoreCase(anyString());
    }

    @Test
    void getCandidateById_whenAdminDoesNotOwnCandidate_throwsRuntimeException() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");
        Candidate candidate = candidate("candidate-1", "Candidate One", "one@example.com", "org-2");

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> candidateService.getCandidateById("candidate-1")
        );

        assertEquals("You are not allowed to access this candidate.", exception.getMessage());
    }

    @Test
    void getCandidateById_whenCandidateOwnsProfile_returnsCandidate() {
        User candidateUser = candidateUser("user-candidate-1", "candidate@example.com");
        Candidate candidate = candidate("candidate-1", "Candidate One", "candidate@example.com", "org-1");
        candidate.setUserId("user-candidate-1");

        setAuthenticatedUser(candidateUser.getEmail());
        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));
        when(candidateRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));

        Candidate result = candidateService.getCandidateById("candidate-1");

        assertEquals("candidate-1", result.getId());
    }

    @Test
    void submitTestResult_whenCandidateOwnsProfile_throwsRuntimeException() {
        User candidateUser = candidateUser("user-candidate-1", "candidate@example.com");

        setAuthenticatedUser(candidateUser.getEmail());
        when(userRepository.findByEmail(candidateUser.getEmail())).thenReturn(Optional.of(candidateUser));

        SubmitTestResultRequest request = new SubmitTestResultRequest();
        request.setTestName("Legacy Score");
        request.setScore(100);
        request.setStatus("PASSED");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> candidateService.submitTestResult("candidate-1", request)
        );

        assertEquals(
                "Only organization staff can record legacy test results.",
                exception.getMessage()
        );

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    @Test
    void createCandidate_normalizesEmailBeforeDuplicateCheckAndSave() {
        User admin = adminUser("admin-1", "admin@skillsync.com", "org-1");

        setAuthenticatedUser(admin.getEmail());
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(candidateRepository.existsByOrganizationIdAndEmailIgnoreCase("org-1", "candidate@example.com"))
                .thenReturn(false);
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.empty());
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCandidateRequest request = createCandidateRequest(
                "Candidate Demo",
                " CANDIDATE@EXAMPLE.COM "
        );

        candidateService.createCandidate(request);

        verify(candidateRepository)
                .existsByOrganizationIdAndEmailIgnoreCase("org-1", "candidate@example.com");

        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());

        assertEquals("candidate@example.com", candidateCaptor.getValue().getEmail());
    }

    private void setAuthenticatedUser(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null)
        );
    }

    private User adminUser(String id, String email, String organizationId) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(Role.ADMIN);
        user.setOrganizationId(organizationId);
        return user;
    }

    private User candidateUser(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(Role.CANDIDATE);
        return user;
    }

    private Candidate candidate(
            String id,
            String name,
            String email,
            String organizationId
    ) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setName(name);
        candidate.setEmail(email);
        candidate.setOrganizationId(organizationId);
        return candidate;
    }

    private CreateCandidateRequest createCandidateRequest(String name, String email) {
        CreateCandidateRequest request = new CreateCandidateRequest();
        request.setName(name);
        request.setEmail(email);
        return request;
    }
}
