package app.SkillSync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.email.from:no-reply@skillsync.local}")
    private String fromEmail;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String fullName, String verificationLink) {
        String subject = "Verify your SkillSync email";
        String body = """
                Hi %s,

                Welcome to SkillSync.

                Please verify your email address by opening this link:

                %s

                This link will expire. If you did not create a SkillSync account, you can ignore this email.

                SkillSync Team
                """.formatted(fullName, verificationLink);

        sendEmail(to, subject, body);
    }

    public void sendPasswordResetEmail(String to, String fullName, String resetLink) {
        String subject = "Reset your SkillSync password";
        String body = """
                Hi %s,

                We received a request to reset your SkillSync password.

                Open this link to choose a new password:

                %s

                This link will expire soon. If you did not request a password reset, you can ignore this email.

                SkillSync Team
                """.formatted(fullName, resetLink);

        sendEmail(to, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        if (!StringUtils.hasText(mailHost) || !StringUtils.hasText(mailUsername)) {
            logger.warn(
                    "Mail is not configured. Email to {} skipped. Subject: {} Body:\n{}",
                    to,
                    subject,
                    body
            );
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
        } catch (Exception exception) {
            logger.error(
                    "Failed to send email to {}. Subject: {}. Body:\n{}",
                    to,
                    subject,
                    body,
                    exception
            );
        }
    }
}