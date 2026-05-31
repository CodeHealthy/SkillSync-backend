package app.SkillSync.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimitServiceTest {

    @Test
    void checkPasswordFlowAllowed_whenLimitExceeded_blocksFurtherAttempts() {
        AuthRateLimitService service = new AuthRateLimitService();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> service.checkPasswordFlowAllowed("127.0.0.1:user@example.com"));
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.checkPasswordFlowAllowed("127.0.0.1:user@example.com")
        );

        assertEquals("Too many attempts. Please wait a few minutes and try again.", exception.getMessage());
    }

    @Test
    void checkAssessmentSubmitAllowed_whenLimitExceeded_blocksFurtherAttempts() {
        AuthRateLimitService service = new AuthRateLimitService();

        for (int attempt = 0; attempt < 12; attempt++) {
            assertDoesNotThrow(() -> service.checkAssessmentSubmitAllowed("127.0.0.1:assignment-1"));
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.checkAssessmentSubmitAllowed("127.0.0.1:assignment-1")
        );

        assertEquals("Too many attempts. Please wait a few minutes and try again.", exception.getMessage());
    }
}
