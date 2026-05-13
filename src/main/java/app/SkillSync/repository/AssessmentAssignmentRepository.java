package app.SkillSync.repository;

import app.SkillSync.model.AssessmentAssignment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AssessmentAssignmentRepository extends MongoRepository<AssessmentAssignment, String> {

    List<AssessmentAssignment> findByCandidateEmailIgnoreCase(String candidateEmail);

    List<AssessmentAssignment> findByCandidateId(String candidateId);

    boolean existsByAssessmentIdAndCandidateId(String assessmentId, String candidateId);
    List<AssessmentAssignment> findByOrganizationId(String organizationId);

    List<AssessmentAssignment> findByCandidateIdIn(List<String> candidateIds);
}