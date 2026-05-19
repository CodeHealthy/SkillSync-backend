package app.SkillSync.service;

import app.SkillSync.dto.AiAssessmentRequest;
import app.SkillSync.dto.AiAssessmentResponse;
import app.SkillSync.model.AssessmentTestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class AiAssessmentService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.features.enabled:false}")
    private boolean aiFeaturesEnabled;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    @Value("${gemini.timeout-seconds:30}")
    private int timeoutSeconds;

    public AiAssessmentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AiAssessmentResponse generateAssessment(AiAssessmentRequest request) {
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
            String prompt = buildPrompt(request);
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
                System.out.println("Gemini status code: " + response.statusCode());
                System.out.println("Gemini response body: " + response.body());

                throw new ResponseStatusException(
                        SERVICE_UNAVAILABLE,
                        "AI model is currently busy. Please try again in a few minutes."
                );
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.out.println("Gemini status code: " + response.statusCode());
                System.out.println("Gemini response body: " + response.body());

                throw new ResponseStatusException(
                        BAD_GATEWAY,
                        "AI provider returned an error."
                );
            }

            String outputText = extractOutputText(response.body());
            return parseAssessmentJson(outputText, request);

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            System.out.println("AI generation error: " + ex.getMessage());

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

    private String buildPrompt(AiAssessmentRequest request) {
        boolean codingChallenge = "CODING_CHALLENGE".equalsIgnoreCase(
                safe(request.getAssessmentType())
        );

        String codingRules = codingChallenge
                ? """
                Coding challenge rules:
                - Generate a complete runnable program starterCode, not only a function.
                - The starterCode must read input from stdin.
                - The candidate program must write final answers to stdout.
                - Docker grading will run the submitted program once per test case.
                - Docker grading compares stdout exactly with each test case expectedOutput after trimming whitespace.
                - expectedOutput must contain ONLY exact stdout text. No explanations, labels, markdown, or sentences.
                - Generate 4 to 6 test cases.
                - At least 1 test case must be visible with "hidden": false.
                - At least 2 test cases should be hidden with "hidden": true.
                - Test cases should include normal cases and edge cases.
                - Sum of all testCases points must equal maxScore.
                - Use simple stdin formats that candidates can reasonably parse.
                - Do not create interactive prompts such as "Enter a number:".
                - For Java, starterCode must use public class Main.
                - For JavaScript, starterCode should read stdin using fs.readFileSync(0, "utf8").
                - For Python, starterCode should read stdin using sys.stdin.read().
                - For linked list, tree, array, or object problems, use deterministic stdin and print a deterministic string.
                """
                : """
                Quiz rules:
                - For QUIZ, starterCode must be an empty string.
                - For QUIZ, expectedOutput must be an empty string.
                - For QUIZ, testCases must be an empty array.
                """;

        return """
                You are helping an admin create a technical assessment draft for SkillSync.

                Return ONLY valid JSON. Do not include markdown. Do not include explanations outside JSON.

                JSON shape:
                {
                  "title": "string",
                  "description": "string",
                  "prompt": "string",
                  "starterCode": "string",
                  "expectedOutput": "string",
                  "maxScore": 100,
                  "rubric": "string",
                  "testCases": [
                    {
                      "name": "string",
                      "input": "string",
                      "expectedOutput": "string",
                      "hidden": false,
                      "points": 25
                    }
                  ]
                }

                General rules:
                - The admin must review before saving, so generate a draft only.
                - Keep the prompt clear, professional, and candidate-facing.
                - Do not include unsafe, offensive, discriminatory, or biased content.
                - maxScore should usually be 100.
                - rubric should explain how the assessment should be reviewed.
                - Do not include secret answer explanations in the candidate prompt.
                - Do not mention SkillSync internals, Docker, hidden tests, or exact grading implementation in the candidate prompt.

                %s

                Assessment request:
                Role/title: %s
                Skill/topic: %s
                Difficulty: %s
                Assessment type: %s
                Language: %s
                Extra context/duration: %s
                """.formatted(
                codingRules,
                safe(request.getRoleTitle()),
                safe(request.getSkillTopic()),
                safe(request.getDifficulty()),
                safe(request.getAssessmentType()),
                safe(request.getLanguage()),
                safe(request.getContext())
        );
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

        if (response.getPrompt() == null || response.getPrompt().isBlank()) {
            throw new IllegalStateException("AI response missing prompt.");
        }

        normalizeResponse(response, request);

        return response;
    }

    private void normalizeResponse(
            AiAssessmentResponse response,
            AiAssessmentRequest request
    ) {
        boolean codingChallenge = "CODING_CHALLENGE".equalsIgnoreCase(
                safe(request.getAssessmentType())
        );

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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}