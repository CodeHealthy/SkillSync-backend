package app.SkillSync.repository;

import app.SkillSync.model.ProcessedWebhookEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedWebhookEventRepository extends MongoRepository<ProcessedWebhookEvent, String> {
}
