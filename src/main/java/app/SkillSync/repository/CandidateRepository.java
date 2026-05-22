package app.SkillSync.repository;

import app.SkillSync.model.Candidate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface CandidateRepository extends MongoRepository<Candidate, String> {

    List<Candidate> findByNameContainingIgnoreCase(String name);

    boolean existsByEmail(String email);
    List<Candidate> findAllByUserId(String userId);
    Optional<Candidate> findByOrganizationIdAndEmailIgnoreCase(String organizationId, String email);
    List<Candidate> findAllByEmailIgnoreCase(String email);
    List<Candidate> findByOrganizationId(String organizationId);
    Optional<Candidate> findByUserId(String userId);
    boolean existsByOrganizationIdAndEmailIgnoreCase(String organizationId, String email);
    long countByOrganizationIdAndCreatedAtBetween(
            String organizationId,
            Instant start,
            Instant end
    );
}
