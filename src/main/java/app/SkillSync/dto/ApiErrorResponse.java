package app.SkillSync.dto;

import java.time.Instant;

public class ApiErrorResponse {

    private String message;
    private int status;
    private String path;
    private Instant timestamp;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String message, int status, String path) {
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = Instant.now();
    }

    public String getMessage() {
        return message;
    }

    public int getStatus() {
        return status;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}