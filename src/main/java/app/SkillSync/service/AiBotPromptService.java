package app.SkillSync.service;

import app.SkillSync.dto.AiAssessmentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class AiBotPromptService {

    private static final String ASSESSMENT_GENERATOR_PROMPT =
            "ai-bots/assessment-generator-professional.prompt";
    private static final String CODING_RULES_PROMPT =
            "ai-bots/rules/coding-challenge.prompt";
    private static final String MCQ_RULES_PROMPT =
            "ai-bots/rules/mcq-short-answer.prompt";

    public String buildAssessmentGeneratorPrompt(AiAssessmentRequest request) {
        boolean includeCoding = includesCoding(request);

        String template = readResource(ASSESSMENT_GENERATOR_PROMPT);
        String assessmentTypeRules = readResource(
                includeCoding ? CODING_RULES_PROMPT : MCQ_RULES_PROMPT
        );

        return template
                .replace("{{assessment_type_rules}}", assessmentTypeRules)
                .replace("{{role_title}}", safe(request.getRoleTitle()))
                .replace("{{skill_topic}}", safe(request.getSkillTopic()))
                .replace("{{difficulty}}", safe(request.getDifficulty()))
                .replace("{{assessment_type}}", includeCoding ? "CODING_CHALLENGE" : "MCQ")
                .replace("{{language}}", includeCoding ? safe(request.getLanguage()) : "TEXT")
                .replace("{{include_coding}}", Boolean.toString(includeCoding))
                .replace("{{include_mcq}}", Boolean.toString(includesMultipleChoice(request)))
                .replace("{{include_short_answer}}", Boolean.toString(includesShortAnswer(request)))
                .replace("{{question_count}}", safeNumber(request.getQuestionCount(), 5))
                .replace("{{duration_minutes}}", safeNumber(request.getDurationMinutes(), 45))
                .replace("{{context}}", safe(request.getContext()));
    }

    private String readResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("AI bot prompt file is missing: " + path, exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean includesCoding(AiAssessmentRequest request) {
        return Boolean.TRUE.equals(request.getIncludeCoding())
                || "CODING_CHALLENGE".equalsIgnoreCase(safe(request.getAssessmentType()))
                || "FULL_ASSESSMENT".equalsIgnoreCase(safe(request.getAssessmentType()));
    }

    private boolean includesMultipleChoice(AiAssessmentRequest request) {
        return request.getIncludeMultipleChoice() == null
                || Boolean.TRUE.equals(request.getIncludeMultipleChoice());
    }

    private boolean includesShortAnswer(AiAssessmentRequest request) {
        return request.getIncludeShortAnswer() == null
                || Boolean.TRUE.equals(request.getIncludeShortAnswer());
    }

    private String safeNumber(Integer value, int fallback) {
        return Integer.toString(value == null || value <= 0 ? fallback : value);
    }
}
