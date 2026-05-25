package app.SkillSync.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
        name = "app.email.provider",
        havingValue = "smtp",
        matchIfMissing = true
)
public class SmtpEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.email.from:no-reply@skillsync.local}")
    private String fromEmail;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage message) {
        if (!StringUtils.hasText(mailHost) || !StringUtils.hasText(mailUsername)) {
            logger.warn(
                    "SMTP mail is not configured. Email to {} skipped. Subject: {} Body:\n{}",
                    message.getTo(),
                    message.getSubject(),
                    message.getBody()
            );
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromEmail);
            mailMessage.setTo(message.getTo());
            mailMessage.setSubject(message.getSubject());
            mailMessage.setText(message.getBody());

            mailSender.send(mailMessage);
        } catch (Exception exception) {
            logger.error(
                    "Failed to send SMTP email to {}. Subject: {}. Body:\n{}",
                    message.getTo(),
                    message.getSubject(),
                    message.getBody(),
                    exception
            );
        }
    }
}
