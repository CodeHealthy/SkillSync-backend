package app.SkillSync.controller;

import app.SkillSync.model.Candidate;
import app.SkillSync.model.TestResult;
import app.SkillSync.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    @Autowired
    private CandidateService candidateService;

    @PostMapping
    public ResponseEntity<Candidate> createCandidate(@RequestBody Candidate candidate) {
        Candidate createdCandidate = candidateService.saveCandidate(candidate);
        return ResponseEntity.status(201).body(createdCandidate);
    }

    @GetMapping
    public ResponseEntity<List<Candidate>> getAllCandidates() {
        List<Candidate> candidates = candidateService.getAllCandidates();
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Candidate>> getCandidatesByName(@RequestParam String name) {
        List<Candidate> candidates = candidateService.getCandidatesByName(name);
        return ResponseEntity.ok(candidates);
    }

    // Endpoint to save test result for a candidate
    @PostMapping("/{candidateId}/test-results")
    public ResponseEntity<TestResult> createTestResult(@PathVariable String candidateId, @RequestBody TestResult testResult) {
        TestResult createdTestResult = candidateService.saveTestResult(candidateId, testResult);
        return ResponseEntity.status(201).body(createdTestResult);
    }

    // Endpoint to get test results for a candidate
    @GetMapping("/{candidateId}/test-results")
    public ResponseEntity<List<TestResult>> getTestResults(@PathVariable String candidateId) {
        List<TestResult> testResults = candidateService.getTestResultsByCandidateId(candidateId);
        return ResponseEntity.ok(testResults);
    }
}