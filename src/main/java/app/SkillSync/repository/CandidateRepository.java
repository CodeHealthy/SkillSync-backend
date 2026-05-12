package app.SkillSync.repository;

import app.SkillSync.model.Candidate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CandidateRepository extends MongoRepository<Candidate, String> {

    List<Candidate> findByNameContainingIgnoreCase(String name);

    boolean existsByEmail(String email);
}