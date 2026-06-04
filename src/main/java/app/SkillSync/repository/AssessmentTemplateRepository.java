package app.SkillSync.repository;

import app.SkillSync.model.AssessmentTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AssessmentTemplateRepository extends MongoRepository<AssessmentTemplate, String> {
    List<AssessmentTemplate> findByActiveTrueAndOrganizationIdIsNullOrderByDisplayOrderAscNameAsc();
    List<AssessmentTemplate> findByActiveTrueAndOrganizationIdOrderByDisplayOrderAscNameAsc(String organizationId);
}
