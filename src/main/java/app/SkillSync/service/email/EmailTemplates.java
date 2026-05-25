package app.SkillSync.service.email;

import org.springframework.stereotype.Component;

@Component
public class EmailTemplates {

    public EmailTemplate verificationEmail(String fullName, String verificationLink) {
        return new EmailTemplate(
                "Verify your SkillSync email",
                """
                Hi %s,

                Welcome to SkillSync.

                Please verify your email address by opening this link:

                %s

                This link will expire. If you did not create a SkillSync account, you can ignore this email.

                SkillSync Team
                """.formatted(fullName, verificationLink)
        );
    }

    public EmailTemplate passwordResetEmail(String fullName, String resetLink) {
        return new EmailTemplate(
                "Reset your SkillSync password",
                """
                Hi %s,

                We received a request to reset your SkillSync password.

                Open this link to choose a new password:

                %s

                This link will expire soon. If you did not request a password reset, you can ignore this email.

                SkillSync Team
                """.formatted(fullName, resetLink)
        );
    }

    public EmailTemplate candidateInviteEmail(String fullName, String inviteLink) {
        return new EmailTemplate(
                "You have been invited to SkillSync",
                """
                Hi %s,

                You have been invited to complete an assessment on SkillSync.

                Open this secure link to accept the invite and create your candidate account:

                %s

                This link will expire. If you were not expecting this invite, you can ignore this email.

                SkillSync Team
                """.formatted(fullName, inviteLink)
        );
    }

    public EmailTemplate teamMemberInviteEmail(
            String fullName,
            String organizationName,
            String inviteLink
    ) {
        return new EmailTemplate(
                "Join your SkillSync team",
                """
                Hi %s,

                You have been invited to join %s on SkillSync.

                Open this secure link to accept the invite and create your account:

                %s

                This link will expire. If you were not expecting this invite, you can ignore this email.

                SkillSync Team
                """.formatted(fullName, organizationName, inviteLink)
        );
    }
}
