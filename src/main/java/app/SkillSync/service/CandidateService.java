package app.SkillSync.service;

import app.SkillSync.dto.CreateCandidateRequest;
import app.SkillSync.dto.SubmitTestResultRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Role;
import app.SkillSync.model.TestResult;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final BillingService billingService;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final OrganizationAccessService organizationAccessService;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public CandidateService(
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            BillingService billingService,
            EmailTokenService emailTokenService,
            MailService mailService,
            AuditLogService auditLogService,
            OrganizationAccessService organizationAccessService
    ) {
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.billingService = billingService;
        this.emailTokenService = emailTokenService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.organizationAccessService = organizationAccessService;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found."));
    }

    public List<Candidate> getAllCandidates() {
        User adminUser = getCurrentUser();

        String organizationId = adminUser.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        organizationAccessService.requireActiveOrganization(organizationId);

        return candidateRepository.findByOrganizationId(organizationId);
    }

    public List<Candidate> searchCandidatesByName(String name) {
        User adminUser = getCurrentUser();
        String organizationId = requireAdminOrganizationId(adminUser);

        return candidateRepository.findByOrganizationIdAndNameContainingIgnoreCase(
                organizationId,
                name
        );
    }

    public Candidate getCandidateById(String candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        validateCurrentUserCanAccessCandidate(candidate);

        return candidate;
    }

    public TestResult submitTestResult(String candidateId, SubmitTestResultRequest request) {
        User user = getCurrentUser();

        if (user.getRole() == null || !user.getRole().isOrganizationStaff()) {
            throw new RuntimeException("Only organization staff can record legacy test results.");
        }

        Candidate candidate = getCandidateById(candidateId);

        if (candidate.getTestResults() == null) {
            candidate.setTestResults(new ArrayList<>());
        }

        TestResult testResult = new TestResult();
        testResult.setTestName(request.getTestName().trim());
        testResult.setScore(request.getScore());
        testResult.setStatus(request.getStatus().trim());
        testResult.setAnswers(request.getAnswers());
        testResult.setSubmissionTime(Instant.now());

        candidate.getTestResults().add(testResult);

        candidateRepository.save(candidate);

        return testResult;
    }

    public List<TestResult> getTestResultsByCandidateId(String candidateId) {
        Candidate candidate = getCandidateById(candidateId);

        if (candidate.getTestResults() == null) {
            return List.of();
        }

        return candidate.getTestResults();
    }

    public Candidate createCandidate(CreateCandidateRequest request) {
        User adminUser = getCurrentUser();

        String organizationId = requireAdminOrganizationId(adminUser);

        if (adminUser.getRole() == null || !adminUser.getRole().canInviteCandidates()) {
            throw new RuntimeException("You are not allowed to invite candidates.");
        }

        billingService.ensureCanInviteCandidate(organizationId);

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (candidateRepository.existsByOrganizationIdAndEmailIgnoreCase(organizationId, normalizedEmail)) {
            throw new RuntimeException("Candidate already exists in your organization.");
        }

        Candidate candidate = new Candidate();
        candidate.setName(request.getName().trim());
        candidate.setEmail(normalizedEmail);
        candidate.setOrganizationId(organizationId);
        candidate.setCreatedByAdminId(adminUser.getId());
        candidate.setCreatedAt(Instant.now());
        candidate.setStatus("INVITED");

        userRepository.findByEmail(normalizedEmail)
                .filter(existingUser -> existingUser.getRole() == Role.CANDIDATE)
                .ifPresent(candidateUser -> {
                    candidate.setUserId(candidateUser.getId());
                    candidate.setStatus("REGISTERED");
                });

        Candidate savedCandidate = candidateRepository.save(candidate);

        if ("INVITED".equals(savedCandidate.getStatus())) {
            sendCandidateInviteEmail(savedCandidate);
        }

        auditLogService.record(
                adminUser,
                "CANDIDATE_INVITED",
                "CANDIDATE",
                savedCandidate.getId(),
                Map.of(
                        "candidateEmail", savedCandidate.getEmail(),
                        "status", savedCandidate.getStatus()
                )
        );

        return savedCandidate;
    }

    private void sendCandidateInviteEmail(Candidate candidate) {
        String rawToken = emailTokenService.createCandidateInviteToken(candidate);

        String inviteLink = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/accept-invite")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        mailService.sendCandidateInviteEmail(
                candidate.getEmail(),
                candidate.getName(),
                inviteLink
        );
    }

    private String requireAdminOrganizationId(User adminUser) {
        String organizationId = adminUser.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        organizationAccessService.requireActiveOrganization(organizationId);
        return organizationId;
    }

    private void validateCurrentUserCanAccessCandidate(Candidate candidate) {
        User user = getCurrentUser();

        if (user.getRole() != null && user.getRole().isOrganizationStaff()) {
            String organizationId = requireAdminOrganizationId(user);

            if (!organizationId.equals(candidate.getOrganizationId())) {
                throw new RuntimeException("You are not allowed to access this candidate.");
            }

            return;
        }

        if (user.getRole() == Role.CANDIDATE &&
                user.getId() != null &&
                user.getId().equals(candidate.getUserId())) {
            return;
        }

        throw new RuntimeException("You are not allowed to access this candidate.");
    }
}
