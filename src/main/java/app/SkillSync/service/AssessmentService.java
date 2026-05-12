package app.SkillSync.service;

import app.SkillSync.dto.AssignAssessmentRequest;
import app.SkillSync.dto.CreateAssessmentRequest;
import app.SkillSync.dto.GradeAssignmentRequest;
import app.SkillSync.dto.SubmitAssignmentRequest;
import app.SkillSync.model.*;
import app.SkillSync.repository.AssessmentAssignmentRepository;
import app.SkillSync.repository.AssessmentRepository;
import app.SkillSync.repository.CandidateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentAssignmentRepository assignmentRepository;
    private final CandidateRepository candidateRepository;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            AssessmentAssignmentRepository assignmentRepository,
            CandidateRepository candidateRepository
    ) {
        this.assessmentRepository = assessmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.candidateRepository = candidateRepository;
    }

    public Assessment createAssessment(CreateAssessmentRequest request) {
        Assessment assessment = new Assessment();
        assessment.setTitle(request.getTitle().trim());
        assessment.setDescription(request.getDescription());
        assessment.setType(request.getType());
        assessment.setLanguage(resolveLanguage(request));
        assessment.setMaxScore(request.getMaxScore());
        assessment.setPrompt(request.getPrompt().trim());
        assessment.setStarterCode(request.getStarterCode());
        assessment.setExpectedOutput(request.getExpectedOutput());
        assessment.setCreatedAt(Instant.now());

        return assessmentRepository.save(assessment);
    }

    public List<Assessment> getAllAssessments() {
        return assessmentRepository.findAll();
    }

    public Assessment getAssessmentById(String assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
    }

    public AssessmentAssignment assignAssessment(AssignAssessmentRequest request) {
        Assessment assessment = getAssessmentById(request.getAssessmentId());

        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        boolean alreadyAssigned = assignmentRepository.existsByAssessmentIdAndCandidateId(
                assessment.getId(),
                candidate.getId()
        );

        if (alreadyAssigned) {
            throw new IllegalArgumentException("Assessment is already assigned to this candidate");
        }

        AssessmentAssignment assignment = new AssessmentAssignment();
        assignment.setAssessmentId(assessment.getId());
        assignment.setAssessmentTitle(assessment.getTitle());
        assignment.setAssessmentType(assessment.getType());
        assignment.setLanguage(assessment.getLanguage());
        assignment.setPrompt(assessment.getPrompt());
        assignment.setStarterCode(assessment.getStarterCode());
        assignment.setExpectedOutput(assessment.getExpectedOutput());

        assignment.setCandidateId(candidate.getId());
        assignment.setCandidateName(candidate.getName());
        assignment.setCandidateEmail(candidate.getEmail());

        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setExecutionStatus("NOT_RUN");
        assignment.setAssignedAt(Instant.now());

        return assignmentRepository.save(assignment);
    }

    public List<AssessmentAssignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    public List<AssessmentAssignment> getAssignmentsForCandidateEmail(String candidateEmail) {
        return assignmentRepository.findByCandidateEmailIgnoreCase(candidateEmail);
    }

    public List<AssessmentAssignment> getAssignmentsForCandidateId(String candidateId) {
        return assignmentRepository.findByCandidateId(candidateId);
    }

    public AssessmentAssignment submitAssignment(
            String assignmentId,
            String loggedInEmail,
            SubmitAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (!assignment.getCandidateEmail().equalsIgnoreCase(loggedInEmail)) {
            throw new IllegalArgumentException("You cannot submit this assignment");
        }

        if (assignment.getStatus() == AssignmentStatus.SUBMITTED || assignment.getStatus() == AssignmentStatus.GRADED) {
            throw new IllegalArgumentException("Assignment has already been submitted");
        }

        boolean isCodingChallenge = assignment.getAssessmentType() == AssessmentType.CODING_CHALLENGE;

        if (isCodingChallenge) {
            if (request.getSubmittedCode() == null || request.getSubmittedCode().trim().isEmpty()) {
                throw new IllegalArgumentException("Submitted code is required");
            }

            assignment.setSubmittedCode(request.getSubmittedCode().trim());
            assignment.setSubmittedAnswer(null);
            assignment.setExecutionStatus("PENDING_EXECUTION");
        } else {
            if (request.getSubmittedAnswer() == null || request.getSubmittedAnswer().trim().isEmpty()) {
                throw new IllegalArgumentException("Submitted answer is required");
            }

            assignment.setSubmittedAnswer(request.getSubmittedAnswer().trim());
            assignment.setSubmittedCode(null);
            assignment.setExecutionStatus("NOT_APPLICABLE");
        }

        assignment.setStatus(AssignmentStatus.SUBMITTED);
        assignment.setSubmittedAt(Instant.now());

        return assignmentRepository.save(assignment);
    }

    public AssessmentAssignment gradeAssignment(
            String assignmentId,
            GradeAssignmentRequest request
    ) {
        AssessmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("Assignment has not been submitted yet");
        }

        assignment.setScore(request.getScore());
        assignment.setFeedback(request.getFeedback());
        assignment.setStatus(AssignmentStatus.GRADED);
        assignment.setGradedAt(Instant.now());

        return assignmentRepository.save(assignment);
    }

    private ProgrammingLanguage resolveLanguage(CreateAssessmentRequest request) {
        if (request.getType() == AssessmentType.QUIZ) {
            return ProgrammingLanguage.TEXT;
        }

        if (request.getLanguage() == null || request.getLanguage() == ProgrammingLanguage.TEXT) {
            return ProgrammingLanguage.JAVA;
        }

        return request.getLanguage();
    }
}