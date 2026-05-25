package app.SkillSync.service;

import app.SkillSync.service.email.EmailMessage;
import app.SkillSync.service.email.EmailSender;
import app.SkillSync.service.email.EmailTemplate;
import app.SkillSync.service.email.EmailTemplates;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final EmailSender emailSender;
    private final EmailTemplates emailTemplates;

    public MailService(EmailSender emailSender, EmailTemplates emailTemplates) {
        this.emailSender = emailSender;
        this.emailTemplates = emailTemplates;
    }

    public void sendVerificationEmail(String to, String fullName, String verificationLink) {
        sendEmail(to, emailTemplates.verificationEmail(fullName, verificationLink));
    }

    public void sendPasswordResetEmail(String to, String fullName, String resetLink) {
        sendEmail(to, emailTemplates.passwordResetEmail(fullName, resetLink));
    }

    public void sendCandidateInviteEmail(String to, String fullName, String inviteLink) {
        sendEmail(to, emailTemplates.candidateInviteEmail(fullName, inviteLink));
    }

    public void sendTeamMemberInviteEmail(
            String to,
            String fullName,
            String organizationName,
            String inviteLink
    ) {
        sendEmail(to, emailTemplates.teamMemberInviteEmail(
                fullName,
                organizationName,
                inviteLink
        ));
    }

    private void sendEmail(String to, EmailTemplate template) {
        emailSender.send(new EmailMessage(
                to,
                template.getSubject(),
                template.getBody()
        ));
    }
}
