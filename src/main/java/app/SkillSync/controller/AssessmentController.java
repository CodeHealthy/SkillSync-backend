package app.SkillSync.controller;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.model.Assessment;
import app.SkillSync.model.AssessmentAssignment;
import app.SkillSync.service.AssessmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.dto.RunCodeRequest;

import java.util.List;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Assessment> createAssessment(
            @Valid @RequestBody CreateAssessmentRequest request
    ) {
        Assessment assessment = assessmentService.createAssessment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assessment);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Assessment>> getAllAssessments() {
        return ResponseEntity.ok(assessmentService.getAllAssessments());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign")
    public ResponseEntity<AssessmentAssignment> assignAssessment(
            @Valid @RequestBody AssignAssessmentRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.assignAssessment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/assignments")
    public ResponseEntity<List<AssessmentAssignment>> getAllAssignments() {
        return ResponseEntity.ok(assessmentService.getAllAssignments());
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @GetMapping("/my-assignments")
    public ResponseEntity<List<AssessmentAssignment>> getMyAssignments() {
        return ResponseEntity.ok(
                assessmentService.getAssignmentsForCurrentCandidate()
        );
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping("/assignments/{assignmentId}/submit")
    public ResponseEntity<AssessmentAssignment> submitAssignment(
            @PathVariable String assignmentId,
            @Valid @RequestBody SubmitAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.submitAssignment(
                assignmentId,
                request
        );

        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/assignments/{assignmentId}/grade")
    public ResponseEntity<AssessmentAssignment> gradeAssignment(
            @PathVariable String assignmentId,
            @Valid @RequestBody GradeAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.gradeAssignment(
                assignmentId,
                request
        );

        return ResponseEntity.ok(assignment);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assignments/{assignmentId}/execute")
    public ResponseEntity<AssessmentAssignment> executeAssignment(
            @PathVariable String assignmentId
    ) {
        AssessmentAssignment assignment = assessmentService.executeAssignment(assignmentId);
        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping("/assignments/{assignmentId}/run")
    public ResponseEntity<CodeExecutionResult> runAssignmentCode(
            @PathVariable String assignmentId,
            @Valid @RequestBody RunCodeRequest request
    ) {
        CodeExecutionResult result = assessmentService.runAssignmentCode(
                assignmentId,
                request
        );

        return ResponseEntity.ok(result);
    }
}