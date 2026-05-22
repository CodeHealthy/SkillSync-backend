package app.SkillSync.repository;

import app.SkillSync.model.Assessment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AssessmentRepository extends MongoRepository<Assessment, String> {
    List<Assessment> findByOrganizationId(String organizationId);
    long countByOrganizationId(String organizationId);
}
