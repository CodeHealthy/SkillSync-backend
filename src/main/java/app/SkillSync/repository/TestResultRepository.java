package app.SkillSync.repository;

import app.SkillSync.model.TestResult;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TestResultRepository extends MongoRepository<TestResult, String> {
    // Custom queries if needed
}