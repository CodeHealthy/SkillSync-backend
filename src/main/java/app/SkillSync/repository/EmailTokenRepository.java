package app.SkillSync.repository;

import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.EmailToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailTokenRepository extends MongoRepository<EmailToken, String> {

    Optional<EmailToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    Optional<EmailToken> findTopByEmailAndTypeOrderByCreatedAtDesc(
            String email,
            AuthTokenType type
    );
}