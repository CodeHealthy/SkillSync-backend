package app.SkillSync.service;

import app.SkillSync.dto.CreateCandidateRequest;
import app.SkillSync.dto.SubmitTestResultRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.TestResult;
import app.SkillSync.repository.CandidateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;

    public CandidateService(CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
    }

    public Candidate createCandidate(CreateCandidateRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (candidateRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Candidate email already exists");
        }

        Candidate candidate = new Candidate();
        candidate.setName(request.getName().trim());
        candidate.setEmail(normalizedEmail);
        candidate.setTestResults(new ArrayList<>());
        candidate.setCreatedAt(Instant.now());

        return candidateRepository.save(candidate);
    }

    public List<Candidate> getAllCandidates() {
        return candidateRepository.findAll();
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
}