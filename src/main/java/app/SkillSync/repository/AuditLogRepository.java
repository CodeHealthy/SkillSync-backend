package app.SkillSync.repository;

import app.SkillSync.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    List<AuditLog> findTop100ByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    List<AuditLog> findTop100ByOrganizationIdAndActionOrderByCreatedAtDesc(
            String organizationId,
            String action
    );

    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    List<AuditLog> findTop200ByActionOrderByCreatedAtDesc(String action);
}
