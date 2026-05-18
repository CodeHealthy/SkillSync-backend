package app.SkillSync.service;

import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.EmailToken;
import app.SkillSync.model.User;
import app.SkillSync.repository.EmailTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.time.Duration;

@Service
public class EmailTokenService {

    private final EmailTokenRepository emailTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.resend-verification-cooldown-seconds:60}")
    private long resendVerificationCooldownSeconds;

    @Value("${app.auth.email-verification-expiration-minutes:1440}")
    private long emailVerificationExpirationMinutes;

    @Value("${app.auth.password-reset-expiration-minutes:30}")
    private long passwordResetExpirationMinutes;

    public EmailTokenService(EmailTokenRepository emailTokenRepository) {
        this.emailTokenRepository = emailTokenRepository;
    }

    public String createEmailVerificationToken(User user) {
        return createToken(user, AuthTokenType.EMAIL_VERIFICATION, emailVerificationExpirationMinutes);
    }

    public String createPasswordResetToken(User user) {
        return createToken(user, AuthTokenType.PASSWORD_RESET, passwordResetExpirationMinutes);
    }

    public EmailToken validateToken(String rawToken, AuthTokenType type) {
        String tokenHash = hashToken(rawToken);

        EmailToken token = emailTokenRepository.findByTokenHashAndType(tokenHash, type)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token."));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("This token has already been used.");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("This token has expired.");
        }

        return token;
    }

    public void markUsed(EmailToken token) {
        token.setUsedAt(Instant.now());
        emailTokenRepository.save(token);
    }

    private String createToken(User user, AuthTokenType type, long expirationMinutes) {
        String rawToken = generateRawToken();

        EmailToken token = new EmailToken();
        token.setUserId(user.getId());
        token.setEmail(user.getEmail());
        token.setTokenHash(hashToken(rawToken));
        token.setType(type);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(expirationMinutes * 60));

        emailTokenRepository.save(token);

        return rawToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void enforceResendVerificationCooldown(String email) {
        emailTokenRepository
                .findTopByEmailAndTypeOrderByCreatedAtDesc(
                        email,
                        AuthTokenType.EMAIL_VERIFICATION
                )
                .ifPresent(token -> {
                    Instant allowedAt = token.getCreatedAt()
                            .plusSeconds(resendVerificationCooldownSeconds);

                    if (allowedAt.isAfter(Instant.now())) {
                        long secondsRemaining = Duration.between(
                                Instant.now(),
                                allowedAt
                        ).toSeconds();

                        throw new IllegalArgumentException(
                                "Please wait " + Math.max(secondsRemaining, 1)
                                        + " seconds before requesting another verification email."
                        );
                    }
                });
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash token.", exception);
        }
    }
}