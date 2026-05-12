package app.SkillSync.repository;

import app.SkillSync.model.Assessment;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssessmentRepository extends MongoRepository<Assessment, String> {
}