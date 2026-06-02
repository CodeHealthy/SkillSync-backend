package app.SkillSync.repository;

import app.SkillSync.model.Organization;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrganizationRepository extends MongoRepository<Organization, String> {
    Optional<Organization> findFirstByNameIgnoreCase(String name);
}
