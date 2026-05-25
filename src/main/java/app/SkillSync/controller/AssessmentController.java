package app.SkillSync.controller;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.AssignmentRunResult;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.dto.RecordIntegrityEventRequest;
import app.SkillSync.dto.RunCodeRequest;
import app.SkillSync.dto.SaveAssignmentDraftRequest;
import app.SkillSync.dto.SectionAttemptRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.model.Assessment;
import app.SkillSync.model.AssessmentAssignment;
import app.SkillSync.service.AssessmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER')")
    @PostMapping
    public ResponseEntity<Assessment> createAssessment(
            @Valid @RequestBody CreateAssessmentRequest request
    ) {
        Assessment assessment = assessmentService.createAssessment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assessment);
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR')")
    @GetMapping
    public ResponseEntity<List<Assessment>> getAllAssessments() {
        return ResponseEntity.ok(assessmentService.getAllAssessments());
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER')")
    @PostMapping("/assign")
    public ResponseEntity<AssessmentAssignment> assignAssessment(
            @Valid @RequestBody AssignAssessmentRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.assignAssessment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','EVALUATOR')")
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
    @PostMapping("/assignments/{assignmentId}/start")
    public ResponseEntity<AssessmentAssignment> startAssignment(
            @PathVariable String assignmentId
    ) {
        AssessmentAssignment assignment = assessmentService.startAssignment(assignmentId);
        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping("/assignments/{assignmentId}/sections/start")
    public ResponseEntity<AssessmentAssignment> startAssignmentSection(
            @PathVariable String assignmentId,
            @Valid @RequestBody SectionAttemptRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.startAssignmentSection(
                assignmentId,
                request.getSectionId()
        );
        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping("/assignments/{assignmentId}/sections/complete")
    public ResponseEntity<AssessmentAssignment> completeAssignmentSection(
            @PathVariable String assignmentId,
            @Valid @RequestBody SectionAttemptRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.completeAssignmentSection(
                assignmentId,
                request.getSectionId()
        );
        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PatchMapping("/assignments/{assignmentId}/draft")
    public ResponseEntity<AssessmentAssignment> saveAssignmentDraft(
            @PathVariable String assignmentId,
            @Valid @RequestBody SaveAssignmentDraftRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.saveAssignmentDraft(
                assignmentId,
                request
        );

        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping("/assignments/{assignmentId}/integrity-events")
    public ResponseEntity<AssessmentAssignment> recordIntegrityEvent(
            @PathVariable String assignmentId,
            @Valid @RequestBody RecordIntegrityEventRequest request
    ) {
        AssessmentAssignment assignment = assessmentService.recordIntegrityEvent(
                assignmentId,
                request
        );

        return ResponseEntity.ok(assignment);
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

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','HIRING_MANAGER','EVALUATOR')")
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

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','HIRING_MANAGER','EVALUATOR')")
    @PostMapping("/assignments/{assignmentId}/execute")
    public ResponseEntity<AssessmentAssignment> executeAssignment(
            @PathVariable String assignmentId
    ) {
        AssessmentAssignment assignment = assessmentService.executeAssignment(assignmentId);
        return ResponseEntity.ok(assignment);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping("/assignments/{assignmentId}/run")
    public ResponseEntity<AssignmentRunResult> runAssignmentCode(
            @PathVariable String assignmentId,
            @Valid @RequestBody RunCodeRequest request
    ) {
        AssignmentRunResult result = assessmentService.runAssignmentCode(
                assignmentId,
                request
        );

        return ResponseEntity.ok(result);
    }
}
