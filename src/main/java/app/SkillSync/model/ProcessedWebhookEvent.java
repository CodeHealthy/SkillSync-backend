package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    private String id;
    private String type;
    private Instant processedAt;

    public ProcessedWebhookEvent() {
    }

    public ProcessedWebhookEvent(String id, String type, Instant processedAt) {
        this.id = id;
        this.type = type;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
