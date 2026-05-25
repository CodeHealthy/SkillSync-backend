package app.SkillSync.repository;

import app.SkillSync.model.User;
import app.SkillSync.model.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    List<User> findByOrganizationId(String organizationId);

    List<User> findByOrganizationIdAndRole(String organizationId, Role role);

    boolean existsByEmail(String email);
}
