package app.SkillSync.service;

import app.SkillSync.model.Candidate;
import app.SkillSync.model.TestResult;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CandidateService {

    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private TestResultRepository testResultRepository;

    public Candidate saveCandidate(Candidate candidate) {
        return candidateRepository.save(candidate);
    }

    public List<Candidate> getAllCandidates() {
        return candidateRepository.findAll();
    }

    public List<Candidate> getCandidatesByName(String name) {
        return candidateRepository.findByName(name);
    }

    // Add test result to candidate
    public TestResult saveTestResult(String candidateId, TestResult testResult) {
        Candidate candidate = candidateRepository.findById(candidateId).orElseThrow(() -> new RuntimeException("Candidate not found"));
        candidate.getTestResults().add(testResult);
        candidateRepository.save(candidate);
        return testResultRepository.save(testResult);
    }

    // Get all test results for a candidate
    public List<TestResult> getTestResultsByCandidateId(String candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId).orElseThrow(() -> new RuntimeException("Candidate not found"));
        return candidate.getTestResults();
    }
}