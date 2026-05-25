package app.SkillSync.service.email;

public class EmailTemplate {

    private final String subject;
    private final String body;

    public EmailTemplate(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }
}
