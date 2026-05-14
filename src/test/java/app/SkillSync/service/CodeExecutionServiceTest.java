package app.SkillSync.service;

import app.SkillSync.model.ProgrammingLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class CodeExecutionServiceTest {

    private CodeExecutionService codeExecutionService;

    @BeforeEach
    void setUp() {
        codeExecutionService = new CodeExecutionService();

        ReflectionTestUtils.setField(codeExecutionService, "executionEnabled", true);
        ReflectionTestUtils.setField(codeExecutionService, "timeoutSeconds", 3L);
        ReflectionTestUtils.setField(codeExecutionService, "maxSourceSizeChars", 100);
        ReflectionTestUtils.setField(codeExecutionService, "maxOutputSizeChars", 1000);
    }

    @Test
    void executeCode_whenExecutionDisabled_throwsIllegalStateException() {
        ReflectionTestUtils.setField(codeExecutionService, "executionEnabled", false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.JAVA,
                        "public class Main { public static void main(String[] args) {} }",
                        ""
                )
        );

        assertEquals("Code execution is disabled", exception.getMessage());
    }

    @Test
    void executeCode_whenLanguageIsNull_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codeExecutionService.executeCode(
                        null,
                        "print('Hello')",
                        "Hello"
                )
        );

        assertEquals("Programming language is required", exception.getMessage());
    }

    @Test
    void executeCode_whenSourceCodeIsNull_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.PYTHON,
                        null,
                        "Hello"
                )
        );

        assertEquals("Source code is required", exception.getMessage());
    }

    @Test
    void executeCode_whenSourceCodeIsBlank_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.PYTHON,
                        "   ",
                        "Hello"
                )
        );

        assertEquals("Source code is required", exception.getMessage());
    }

    @Test
    void executeCode_whenSourceCodeExceedsLimit_throwsIllegalArgumentException() {
        String largeSourceCode = "a".repeat(101);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.PYTHON,
                        largeSourceCode,
                        "Hello"
                )
        );

        assertTrue(exception.getMessage().contains("Source code exceeds maximum allowed size"));
    }

    @Test
    void executeCode_whenLanguageIsText_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.TEXT,
                        "Plain text answer",
                        "Plain text answer"
                )
        );

        assertEquals("Unsupported executable language: TEXT", exception.getMessage());
    }

    @Test
    void executeCode_whenTimeoutIsInvalid_throwsIllegalStateException() {
        ReflectionTestUtils.setField(codeExecutionService, "timeoutSeconds", 0L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.PYTHON,
                        "print('Hello')",
                        "Hello"
                )
        );

        assertEquals("Code execution timeout must be greater than zero", exception.getMessage());
    }

    @Test
    void executeCode_whenMaxOutputSizeIsInvalid_throwsIllegalStateException() {
        ReflectionTestUtils.setField(codeExecutionService, "maxOutputSizeChars", 0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> codeExecutionService.executeCode(
                        ProgrammingLanguage.PYTHON,
                        "print('Hello')",
                        "Hello"
                )
        );

        assertEquals("Maximum output size must be greater than zero", exception.getMessage());
    }
}