package app.SkillSync.controller;

import app.SkillSync.dto.CreateCandidateRequest;
import app.SkillSync.dto.SubmitTestResultRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.TestResult;
import app.SkillSync.service.CandidateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER')")
    @PostMapping
    public ResponseEntity<Candidate> createCandidate(
            @Valid @RequestBody CreateCandidateRequest request
    ) {
        Candidate candidate = candidateService.createCandidate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(candidate);
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR')")
    @GetMapping
    public ResponseEntity<List<Candidate>> getAllCandidates() {
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR')")
    @GetMapping("/{candidateId}")
    public ResponseEntity<Candidate> getCandidateById(
            @PathVariable String candidateId
    ) {
        return ResponseEntity.ok(candidateService.getCandidateById(candidateId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR')")
    @GetMapping("/search")
    public ResponseEntity<List<Candidate>> searchCandidates(
            @RequestParam String name
    ) {
        return ResponseEntity.ok(candidateService.searchCandidatesByName(name));
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR','CANDIDATE')")
    @PostMapping("/{candidateId}/test-results")
    public ResponseEntity<TestResult> submitTestResult(
            @PathVariable String candidateId,
            @Valid @RequestBody SubmitTestResultRequest request
    ) {
        TestResult testResult = candidateService.submitTestResult(candidateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(testResult);
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR')")
    @GetMapping("/{candidateId}/test-results")
    public ResponseEntity<List<TestResult>> getTestResults(
            @PathVariable String candidateId
    ) {
        return ResponseEntity.ok(candidateService.getTestResultsByCandidateId(candidateId));
    }
}
