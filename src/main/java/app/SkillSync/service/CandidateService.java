package app.SkillSync.service;

import app.SkillSync.dto.CreateCandidateRequest;
import app.SkillSync.dto.SubmitTestResultRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Role;
import app.SkillSync.model.TestResult;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;

    public CandidateService(CandidateRepository candidateRepository,UserRepository userRepository) {
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
    }

    public Candidate createCandidate(CreateCandidateRequest request) {
        User adminUser = getCurrentUser();

        String organizationId = adminUser.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

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

        return candidateRepository.save(candidate);
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

        return candidateRepository.findByOrganizationId(organizationId);
    }

    public List<Candidate> searchCandidatesByName(String name) {
        return candidateRepository.findByNameContainingIgnoreCase(name);
    }

    public Candidate getCandidateById(String candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
    }

    public TestResult submitTestResult(String candidateId, SubmitTestResultRequest request) {
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
    public Candidate createCandidate(CreateCandidateRequest request, User adminUser) {
        String organizationId = adminUser.getOrganizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        candidateRepository
                .findByOrganizationIdAndEmailIgnoreCase(organizationId, request.getEmail())
                .ifPresent(existing -> {
                    throw new RuntimeException("Candidate already exists in your organization.");
                });

        Candidate candidate = new Candidate();
        candidate.setName(request.getName());
        candidate.setEmail(request.getEmail().toLowerCase().trim());
        candidate.setOrganizationId(organizationId);
        candidate.setCreatedByAdminId(adminUser.getId());
        candidate.setStatus("INVITED");

        userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .filter(user -> "CANDIDATE".equals(user.getRole()))
                .ifPresent(user -> {
                    candidate.setUserId(user.getId());
                    candidate.setStatus("REGISTERED");
                });

        return candidateRepository.save(candidate);
    }
}