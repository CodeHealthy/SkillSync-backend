package app.SkillSync.service;

import app.SkillSync.dto.AiAssessmentRequest;
import app.SkillSync.dto.AiAssessmentResponse;
import app.SkillSync.model.AssessmentQuestion;
import app.SkillSync.model.AssessmentQuestionOption;
import app.SkillSync.model.AssessmentSection;
import app.SkillSync.model.AssessmentTestCase;
import app.SkillSync.model.ProgrammingLanguage;
import app.SkillSync.model.QuestionType;
import app.SkillSync.model.User;
import app.SkillSync.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class AiAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AiAssessmentService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final BillingService billingService;
    private final UserRepository userRepository;
    private final AiBotPromptService aiBotPromptService;

    @Value("${ai.features.enabled:false}")
    private boolean aiFeaturesEnabled;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    @Value("${gemini.timeout-seconds:30}")
    private int timeoutSeconds;

    public AiAssessmentService(
            ObjectMapper objectMapper,
            BillingService billingService,
            UserRepository userRepository,
            AiBotPromptService aiBotPromptService
    ) {
        this.objectMapper = objectMapper;
        this.billingService = billingService;
        this.userRepository = userRepository;
        this.aiBotPromptService = aiBotPromptService;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AiAssessmentResponse generateAssessment(AiAssessmentRequest request) {
        User adminUser = getCurrentUser();

        if (adminUser.getOrganizationId() == null || adminUser.getOrganizationId().isBlank()) {
            throw new RuntimeException("Admin is not linked to an organization.");
        }

        billingService.ensureCanUseAiGeneration(adminUser.getOrganizationId());

        if (!aiFeaturesEnabled) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "AI features are currently disabled."
            );
        }

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "AI provider is not configured."
            );
        }

        try {
            String prompt = aiBotPromptService.buildAssessmentGeneratorPrompt(request);
            String requestBody = buildGeminiRequestBody(prompt);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/"
                                    + geminiModel
                                    + ":generateContent"
                    ))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("x-goog-api-key", geminiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 503) {
                Thread.sleep(1500);

                response = httpClient.send(
                        httpRequest,
                        HttpResponse.BodyHandlers.ofString()
                );
            }

            if (response.statusCode() == 503) {
                log.warn("Gemini provider unavailable. status={}", response.statusCode());

                throw new ResponseStatusException(
                        SERVICE_UNAVAILABLE,
                        "AI model is currently busy. Please try again in a few minutes."
                );
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini provider returned an error. status={}", response.statusCode());

                throw new ResponseStatusException(
                        BAD_GATEWAY,
                        "AI provider returned an error."
                );
            }

            String outputText = extractOutputText(response.body());
            return parseAssessmentJson(outputText, request);

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new ResponseStatusException(
                    GATEWAY_TIMEOUT,
                    "AI generation timed out. Please try again or reduce the draft size."
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "AI generation was interrupted. Please try again."
            );
        } catch (Exception ex) {
            log.warn("AI assessment generation failed: {}", ex.getMessage());

            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Failed to generate assessment draft."
            );
        }
    }

    private String buildGeminiRequestBody(String prompt) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = objectMapper.createArrayNode();

        parts.add(
                objectMapper.createObjectNode()
                        .put("text", prompt)
        );

        content.set("parts", parts);
        contents.add(content);

        ObjectNode generationConfig = objectMapper.createObjectNode()
                .put("temperature", 0.35)
                .put("responseMimeType", "application/json");

        root.set("contents", contents);
        root.set("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(root);
    }

    private String extractOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");

        if (candidates.isArray() && candidates.size() > 0) {
            StringBuilder builder = new StringBuilder();

            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate
                        .path("content")
                        .path("parts");

                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        JsonNode text = part.path("text");

                        if (!text.isMissingNode() && !text.asText().isBlank()) {
                            builder.append(text.asText());
                        }
                    }
                }
            }

            String result = builder.toString().trim();

            if (!result.isBlank()) {
                return result;
            }
        }

        throw new IllegalStateException("No AI output text found.");
    }

    private AiAssessmentResponse parseAssessmentJson(
            String outputText,
            AiAssessmentRequest request
    ) throws Exception {
        String cleanedJson = stripJsonCodeFence(outputText);

        AiAssessmentResponse response = objectMapper.readValue(
                cleanedJson,
                AiAssessmentResponse.class
        );

        if (response.getTitle() == null || response.getTitle().isBlank()) {
            throw new IllegalStateException("AI response missing title.");
        }

        normalizeResponse(response, request);

        if (response.getPrompt() == null || response.getPrompt().isBlank()) {
            throw new IllegalStateException("AI response missing prompt.");
        }

        if (response.getSections() == null || response.getSections().isEmpty()) {
            throw new IllegalStateException("AI response missing assessment sections.");
        }

        return response;
    }

    private void normalizeResponse(
            AiAssessmentResponse response,
            AiAssessmentRequest request
    ) {
        boolean codingChallenge = includesCoding(request);

        response.setRoleTitle(firstPresent(response.getRoleTitle(), request.getRoleTitle()));
        response.setAssessmentType(codingChallenge ? "CODING_CHALLENGE" : "MCQ");
        response.setLanguage(codingChallenge ? normalizeLanguage(request.getLanguage()).name() : "TEXT");
        response.setDurationMinutes(safePositiveInt(
                response.getDurationMinutes() == null
                        ? request.getDurationMinutes()
                        : response.getDurationMinutes()
        ));

        if (response.getDescription() == null) {
            response.setDescription("");
        }

        if (response.getStarterCode() == null) {
            response.setStarterCode("");
        }

        if (response.getExpectedOutput() == null) {
            response.setExpectedOutput("");
        }

        if (response.getRubric() == null) {
            response.setRubric("");
        }

        if (response.getMaxScore() == null || response.getMaxScore() <= 0) {
            response.setMaxScore(100);
        }

        if (!codingChallenge) {
            response.setStarterCode("");
            response.setExpectedOutput("");
            response.setTestCases(new ArrayList<>());
            response.setSections(normalizeSections(response, request, false));
            syncTopLevelFieldsFromSections(response, false);
            return;
        }

        List<AssessmentTestCase> normalizedTestCases = normalizeTestCases(
                response.getTestCases(),
                response.getMaxScore()
        );

        if (normalizedTestCases.isEmpty()
                && response.getExpectedOutput() != null
                && !response.getExpectedOutput().trim().isEmpty()) {
            AssessmentTestCase fallback = new AssessmentTestCase();
            fallback.setName("Sample case");
            fallback.setInput("");
            fallback.setExpectedOutput(response.getExpectedOutput().trim());
            fallback.setHidden(false);
            fallback.setPoints(response.getMaxScore());

            normalizedTestCases.add(fallback);
        }

        if (!normalizedTestCases.isEmpty()) {
            boolean hasVisible = normalizedTestCases.stream()
                    .anyMatch(testCase -> !testCase.isHidden());

            if (!hasVisible) {
                normalizedTestCases.get(0).setHidden(false);
            }

            normalizePoints(normalizedTestCases, response.getMaxScore());

            response.setTestCases(normalizedTestCases);

            response.setExpectedOutput(
                    normalizedTestCases.stream()
                            .filter(testCase -> !testCase.isHidden())
                            .findFirst()
                            .orElse(normalizedTestCases.get(0))
                            .getExpectedOutput()
                );
        }

        response.setSections(normalizeSections(response, request, true));
        syncTopLevelFieldsFromSections(response, true);
    }

    private List<AssessmentSection> normalizeSections(
            AiAssessmentResponse response,
            AiAssessmentRequest request,
            boolean codingChallenge
    ) {
        List<AssessmentSection> sections = response.getSections();

        if (sections == null || sections.isEmpty()) {
            return createFallbackSections(response, request, codingChallenge);
        }

        List<AssessmentSection> normalizedSections = new ArrayList<>();
        int sectionIndex = 1;

        for (AssessmentSection section : sections) {
            if (section == null) {
                continue;
            }

            List<AssessmentQuestion> questions = normalizeQuestions(
                    section.getQuestions(),
                    response,
                    request,
                    codingChallenge
            );

            if (questions.isEmpty()) {
                continue;
            }

            AssessmentSection normalizedSection = new AssessmentSection();
            normalizedSection.setId(firstPresent(section.getId(), "section-" + sectionIndex));
            normalizedSection.setTitle(firstPresent(section.getTitle(), "Assessment section"));
            normalizedSection.setDescription(safe(section.getDescription()));
            normalizedSection.setTimeLimitMinutes(safePositiveInt(section.getTimeLimitMinutes()));
            normalizedSection.setQuestions(questions);

            normalizedSections.add(normalizedSection);
            sectionIndex++;
        }

        if (normalizedSections.isEmpty()) {
            return createFallbackSections(response, request, codingChallenge);
        }

        rebalanceQuestionPoints(normalizedSections, response.getMaxScore());

        return normalizedSections;
    }

    private void rebalanceQuestionPoints(
            List<AssessmentSection> sections,
            int maxScore
    ) {
        List<AssessmentQuestion> questions = sections.stream()
                .flatMap(section -> section.getQuestions().stream())
                .toList();

        if (questions.isEmpty()) {
            return;
        }

        int totalPoints = questions.stream()
                .mapToInt(question -> safeInt(question.getPoints()))
                .sum();

        if (totalPoints == maxScore) {
            return;
        }

        if (totalPoints <= 0 || totalPoints == questions.size() * maxScore) {
            int basePoints = Math.max(maxScore / questions.size(), 1);
            int remaining = maxScore;

            for (int index = 0; index < questions.size(); index++) {
                int points = index == questions.size() - 1
                        ? remaining
                        : Math.min(basePoints, remaining);

                questions.get(index).setPoints(points);
                remaining -= points;
            }

            return;
        }

        int remaining = maxScore;

        for (int index = 0; index < questions.size(); index++) {
            AssessmentQuestion question = questions.get(index);
            int adjustedPoints;

            if (index == questions.size() - 1) {
                adjustedPoints = remaining;
            } else {
                double ratio = (double) safeInt(question.getPoints()) / totalPoints;
                adjustedPoints = Math.max(1, (int) Math.round(maxScore * ratio));
                adjustedPoints = Math.min(adjustedPoints, remaining);
            }

            question.setPoints(adjustedPoints);
            remaining -= adjustedPoints;
        }
    }

    private List<AssessmentQuestion> normalizeQuestions(
            List<AssessmentQuestion> questions,
            AiAssessmentResponse response,
            AiAssessmentRequest request,
            boolean codingChallenge
    ) {
        List<AssessmentQuestion> normalizedQuestions = new ArrayList<>();

        if (questions == null) {
            return normalizedQuestions;
        }

        int questionIndex = 1;

        for (AssessmentQuestion question : questions) {
            if (question == null || safe(question.getPrompt()).isBlank()) {
                continue;
            }

            QuestionType type = question.getType() == null
                    ? (codingChallenge ? QuestionType.CODING_CHALLENGE : QuestionType.SHORT_ANSWER)
                    : normalizeQuestionType(question.getType());

            AssessmentQuestion normalizedQuestion = new AssessmentQuestion();
            normalizedQuestion.setId(firstPresent(question.getId(), "question-" + questionIndex));
            normalizedQuestion.setType(type);
            normalizedQuestion.setTitle(firstPresent(question.getTitle(), "Question " + questionIndex));
            normalizedQuestion.setPrompt(question.getPrompt().trim());
            normalizedQuestion.setPoints(safePositiveInt(question.getPoints(), response.getMaxScore()));

            if (type == QuestionType.CODING_CHALLENGE) {
                normalizedQuestion.setLanguage(normalizeLanguage(request.getLanguage()));
                normalizedQuestion.setStarterCode(firstPresent(
                        question.getStarterCode(),
                        response.getStarterCode()
                ));
                normalizedQuestion.setExpectedOutput(firstPresent(
                        question.getExpectedOutput(),
                        response.getExpectedOutput()
                ));
                normalizedQuestion.setCorrectAnswer("");
                normalizedQuestion.setOptions(new ArrayList<>());
                normalizedQuestion.setTestCases(normalizeTestCases(
                        question.getTestCases() == null || question.getTestCases().isEmpty()
                                ? response.getTestCases()
                                : question.getTestCases(),
                        normalizedQuestion.getPoints()
                ));
            } else if (type == QuestionType.MULTIPLE_CHOICE) {
                normalizedQuestion.setLanguage(ProgrammingLanguage.TEXT);
                normalizedQuestion.setStarterCode("");
                normalizedQuestion.setExpectedOutput("");
                normalizedQuestion.setCorrectAnswer("");
                normalizedQuestion.setOptions(normalizeOptions(question.getOptions()));
                normalizedQuestion.setTestCases(new ArrayList<>());

                if (normalizedQuestion.getOptions().size() < 2) {
                    normalizedQuestion.setType(QuestionType.SHORT_ANSWER);
                    normalizedQuestion.setCorrectAnswer(safe(question.getCorrectAnswer()));
                    normalizedQuestion.setOptions(new ArrayList<>());
                }
            } else {
                normalizedQuestion.setLanguage(ProgrammingLanguage.TEXT);
                normalizedQuestion.setStarterCode("");
                normalizedQuestion.setExpectedOutput("");
                normalizedQuestion.setCorrectAnswer(safe(question.getCorrectAnswer()));
                normalizedQuestion.setOptions(new ArrayList<>());
                normalizedQuestion.setTestCases(new ArrayList<>());
            }

            normalizedQuestions.add(normalizedQuestion);
            questionIndex++;
        }

        return normalizedQuestions;
    }

    private List<AssessmentQuestionOption> normalizeOptions(List<AssessmentQuestionOption> options) {
        List<AssessmentQuestionOption> normalizedOptions = new ArrayList<>();

        if (options == null) {
            return normalizedOptions;
        }

        int optionIndex = 1;
        boolean hasCorrect = false;

        for (AssessmentQuestionOption option : options) {
            if (option == null || safe(option.getText()).isBlank()) {
                continue;
            }

            AssessmentQuestionOption normalizedOption = new AssessmentQuestionOption();
            normalizedOption.setId(firstPresent(option.getId(), "option-" + optionIndex));
            normalizedOption.setText(option.getText().trim());
            normalizedOption.setCorrect(Boolean.TRUE.equals(option.getCorrect()));

            hasCorrect = hasCorrect || Boolean.TRUE.equals(normalizedOption.getCorrect());
            normalizedOptions.add(normalizedOption);
            optionIndex++;
        }

        if (!hasCorrect && !normalizedOptions.isEmpty()) {
            normalizedOptions.get(0).setCorrect(true);
        }

        return normalizedOptions;
    }

    private List<AssessmentSection> createFallbackSections(
            AiAssessmentResponse response,
            AiAssessmentRequest request,
            boolean codingChallenge
    ) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId("question-1");
        question.setType(codingChallenge ? QuestionType.CODING_CHALLENGE : QuestionType.SHORT_ANSWER);
        question.setTitle(firstPresent(response.getTitle(), request.getSkillTopic(), "Generated question"));
        question.setPrompt(firstPresent(response.getPrompt(), response.getDescription()));
        question.setPoints(response.getMaxScore());

        if (codingChallenge) {
            question.setLanguage(normalizeLanguage(request.getLanguage()));
            question.setStarterCode(safe(response.getStarterCode()));
            question.setExpectedOutput(safe(response.getExpectedOutput()));
            question.setCorrectAnswer("");
            question.setOptions(new ArrayList<>());
            question.setTestCases(normalizeTestCases(response.getTestCases(), response.getMaxScore()));
        } else {
            question.setLanguage(ProgrammingLanguage.TEXT);
            question.setStarterCode("");
            question.setExpectedOutput("");
            question.setCorrectAnswer(safe(response.getRubric()));
            question.setOptions(new ArrayList<>());
            question.setTestCases(new ArrayList<>());
        }

        AssessmentSection section = new AssessmentSection();
        section.setId("section-1");
        section.setTitle("AI generated screen");
        section.setDescription(safe(response.getDescription()));
        section.setTimeLimitMinutes(safePositiveInt(response.getDurationMinutes()));
        section.setQuestions(List.of(question));

        return List.of(section);
    }

    private void syncTopLevelFieldsFromSections(
            AiAssessmentResponse response,
            boolean codingChallenge
    ) {
        AssessmentQuestion primaryQuestion = response.getSections().stream()
                .flatMap(section -> section.getQuestions().stream())
                .filter(question -> codingChallenge
                        ? question.getType() == QuestionType.CODING_CHALLENGE
                        : question.getType() != QuestionType.CODING_CHALLENGE)
                .findFirst()
                .orElse(null);

        if (primaryQuestion == null) {
            return;
        }

        response.setPrompt(firstPresent(response.getPrompt(), primaryQuestion.getPrompt()));

        if (codingChallenge) {
            response.setStarterCode(firstPresent(response.getStarterCode(), primaryQuestion.getStarterCode()));
            response.setExpectedOutput(firstPresent(response.getExpectedOutput(), primaryQuestion.getExpectedOutput()));
            response.setTestCases(primaryQuestion.getTestCases());
        } else {
            response.setStarterCode("");
            response.setExpectedOutput("");
            response.setTestCases(new ArrayList<>());
        }

        int totalPoints = response.getSections().stream()
                .flatMap(section -> section.getQuestions().stream())
                .mapToInt(question -> safeInt(question.getPoints()))
                .sum();

        if (totalPoints > 0) {
            response.setMaxScore(totalPoints);
        }
    }

    private List<AssessmentTestCase> normalizeTestCases(
            List<AssessmentTestCase> testCases,
            int maxScore
    ) {
        List<AssessmentTestCase> normalized = new ArrayList<>();

        if (testCases == null) {
            return normalized;
        }

        int index = 1;

        for (AssessmentTestCase testCase : testCases) {
            if (testCase == null) {
                continue;
            }

            String expectedOutput = safe(testCase.getExpectedOutput()).trim();

            if (expectedOutput.isEmpty()) {
                continue;
            }

            AssessmentTestCase normalizedCase = new AssessmentTestCase();
            normalizedCase.setName(
                    safe(testCase.getName()).isBlank()
                            ? "Test case " + index
                            : testCase.getName().trim()
            );
            normalizedCase.setInput(safe(testCase.getInput()));
            normalizedCase.setExpectedOutput(expectedOutput);
            normalizedCase.setHidden(Boolean.TRUE.equals(testCase.getHidden()));
            normalizedCase.setPoints(testCase.getPoints() == null ? 0 : testCase.getPoints());

            normalized.add(normalizedCase);
            index++;
        }

        if (!normalized.isEmpty()) {
            normalizePoints(normalized, maxScore);
        }

        return normalized;
    }

    private void normalizePoints(
            List<AssessmentTestCase> testCases,
            int maxScore
    ) {
        if (testCases == null || testCases.isEmpty()) {
            return;
        }

        int totalPoints = testCases.stream()
                .mapToInt(testCase -> testCase.getPoints() == null ? 0 : testCase.getPoints())
                .sum();

        if (totalPoints == maxScore) {
            return;
        }

        if (totalPoints <= 0) {
            distributePoints(testCases, maxScore);
            return;
        }

        int remaining = maxScore;

        for (int index = 0; index < testCases.size(); index++) {
            AssessmentTestCase testCase = testCases.get(index);

            int adjustedPoints;

            if (index == testCases.size() - 1) {
                adjustedPoints = remaining;
            } else {
                double ratio = (double) safeInt(testCase.getPoints()) / totalPoints;
                adjustedPoints = Math.max(1, (int) Math.round(maxScore * ratio));
                adjustedPoints = Math.min(adjustedPoints, remaining);
            }

            testCase.setPoints(adjustedPoints);
            remaining -= adjustedPoints;
        }

        if (remaining != 0 && !testCases.isEmpty()) {
            AssessmentTestCase last = testCases.get(testCases.size() - 1);
            last.setPoints(Math.max(1, safeInt(last.getPoints()) + remaining));
        }
    }

    private void distributePoints(
            List<AssessmentTestCase> testCases,
            int maxScore
    ) {
        int basePoints = Math.max(maxScore / testCases.size(), 1);
        int remaining = maxScore;

        for (int index = 0; index < testCases.size(); index++) {
            int points = index == testCases.size() - 1
                    ? remaining
                    : Math.min(basePoints, remaining);

            testCases.get(index).setPoints(points);
            remaining -= points;
        }
    }

    private String stripJsonCodeFence(String value) {
        String cleaned = value == null ? "" : value.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        return cleaned;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer safePositiveInt(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private Integer safePositiveInt(Integer value, Integer fallback) {
        if (value != null && value > 0) {
            return value;
        }

        return fallback != null && fallback > 0 ? fallback : 1;
    }

    private QuestionType normalizeQuestionType(QuestionType type) {
        if (type == QuestionType.MULTIPLE_CHOICE
                || type == QuestionType.SHORT_ANSWER
                || type == QuestionType.CODING_CHALLENGE) {
            return type;
        }

        return QuestionType.SHORT_ANSWER;
    }

    private ProgrammingLanguage normalizeLanguage(String value) {
        try {
            return ProgrammingLanguage.valueOf(safe(value).toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ProgrammingLanguage.JAVA;
        }
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private boolean includesCoding(AiAssessmentRequest request) {
        return Boolean.TRUE.equals(request.getIncludeCoding())
                || "CODING_CHALLENGE".equalsIgnoreCase(safe(request.getAssessmentType()))
                || "FULL_ASSESSMENT".equalsIgnoreCase(safe(request.getAssessmentType()));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found."));
    }
}
