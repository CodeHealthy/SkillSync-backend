package app.SkillSync.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {

    private static final int MAX_LOGIN_ATTEMPTS = 8;
    private static final int MAX_REGISTER_ATTEMPTS = 5;
    private static final int MAX_PASSWORD_FLOW_ATTEMPTS = 5;
    private static final int MAX_TOKEN_FLOW_ATTEMPTS = 10;
    private static final int MAX_ASSESSMENT_SUBMIT_ATTEMPTS = 12;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, AttemptBucket> attempts = new ConcurrentHashMap<>();

    public void checkLoginAllowed(String key) {
        checkAllowed("login:" + key, MAX_LOGIN_ATTEMPTS);
    }

    public void checkRegisterAllowed(String key) {
        checkAllowed("register:" + key, MAX_REGISTER_ATTEMPTS);
    }

    public void checkPasswordFlowAllowed(String key) {
        checkAllowed("password:" + key, MAX_PASSWORD_FLOW_ATTEMPTS);
    }

    public void checkTokenFlowAllowed(String key) {
        checkAllowed("token:" + key, MAX_TOKEN_FLOW_ATTEMPTS);
    }

    public void checkAssessmentSubmitAllowed(String key) {
        checkAllowed("assessment-submit:" + key, MAX_ASSESSMENT_SUBMIT_ATTEMPTS);
    }

    public void resetLoginAttempts(String key) {
        attempts.remove("login:" + key);
    }

    private void checkAllowed(String key, int maxAttempts) {
        Instant now = Instant.now();

        AttemptBucket bucket = attempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartedAt.plus(WINDOW).isBefore(now)) {
                return new AttemptBucket(1, now);
            }

            existing.count++;
            return existing;
        });

        if (bucket.count > maxAttempts) {
            throw new IllegalArgumentException(
                    "Too many attempts. Please wait a few minutes and try again."
            );
        }
    }

    private static class AttemptBucket {
        private int count;
        private final Instant windowStartedAt;

        private AttemptBucket(int count, Instant windowStartedAt) {
            this.count = count;
            this.windowStartedAt = windowStartedAt;
        }
    }
}
