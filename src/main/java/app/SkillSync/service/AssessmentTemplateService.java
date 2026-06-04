package app.SkillSync.service;

import app.SkillSync.model.AssessmentQuestion;
import app.SkillSync.model.AssessmentQuestionOption;
import app.SkillSync.model.AssessmentSection;
import app.SkillSync.model.AssessmentTemplate;
import app.SkillSync.model.AssessmentTestCase;
import app.SkillSync.model.AssessmentType;
import app.SkillSync.model.ProgrammingLanguage;
import app.SkillSync.model.QuestionType;
import app.SkillSync.model.User;
import app.SkillSync.repository.AssessmentTemplateRepository;
import app.SkillSync.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssessmentTemplateService {

    private final AssessmentTemplateRepository templateRepository;
    private final UserRepository userRepository;

    public AssessmentTemplateService(
            AssessmentTemplateRepository templateRepository,
            UserRepository userRepository
    ) {
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void seedDefaultTemplates() {
        if (templateRepository.count() > 0) {
            return;
        }

        templateRepository.saveAll(List.of(
                shortAnswerTemplate(),
                codingTemplate(),
                mixedTechnicalTemplate()
        ));
    }

    public List<AssessmentTemplate> listTemplatesForCurrentUser() {
        User user = getCurrentUser();
        List<AssessmentTemplate> templates = new ArrayList<>(
                templateRepository.findByActiveTrueAndOrganizationIdIsNullOrderByDisplayOrderAscNameAsc()
        );

        if (user.getOrganizationId() != null && !user.getOrganizationId().isBlank()) {
            templates.addAll(templateRepository.findByActiveTrueAndOrganizationIdOrderByDisplayOrderAscNameAsc(
                    user.getOrganizationId()
            ));
        }

        return templates;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private AssessmentTemplate shortAnswerTemplate() {
        AssessmentTemplate template = baseTemplate(
                "short-answer-screen",
                "Short-answer screening",
                "A lightweight manual-review screen for communication, judgment, and role understanding.",
                "General screening",
                "Screening",
                AssessmentType.MCQ,
                ProgrammingLanguage.TEXT,
                30,
                60,
                10
        );

        AssessmentSection section = section(
                "short-answer-core",
                "Role understanding",
                "Open-ended questions for reviewer scoring.",
                30
        );
        section.setQuestions(List.of(
                shortAnswer(
                        "q-impact",
                        "Recent project impact",
                        "Describe a recent project where your work created measurable impact. What did you own, what tradeoffs did you make, and how did you measure success?",
                        "Look for ownership, tradeoff reasoning, clarity, and outcome awareness.",
                        20
                ),
                shortAnswer(
                        "q-debug",
                        "Problem solving approach",
                        "A production issue appears shortly after a release. Walk through how you would diagnose, communicate, and resolve it.",
                        "Look for structured debugging, communication, rollback judgment, and prevention steps.",
                        20
                ),
                shortAnswer(
                        "q-collaboration",
                        "Collaboration scenario",
                        "Tell us about a time you disagreed with a teammate or stakeholder. How did you handle it?",
                        "Look for maturity, evidence-based discussion, and ability to preserve trust.",
                        20
                )
        ));
        template.setSections(List.of(section));
        return template;
    }

    private AssessmentTemplate codingTemplate() {
        AssessmentTemplate template = baseTemplate(
                "java-coding-basics",
                "Java coding challenge",
                "A starter programming assessment with visible and hidden test cases.",
                "Java Developer",
                "Programming",
                AssessmentType.CODING_CHALLENGE,
                ProgrammingLanguage.JAVA,
                45,
                100,
                20
        );

        AssessmentSection section = section(
                "coding-core",
                "Coding task",
                "Candidate solves one executable programming question.",
                45
        );
        section.setQuestions(List.of(codingQuestion()));
        template.setSections(List.of(section));
        return template;
    }

    private AssessmentTemplate mixedTechnicalTemplate() {
        AssessmentTemplate template = baseTemplate(
                "technical-mixed-screen",
                "Mixed technical screen",
                "A balanced screen with MCQ, short answer, and coding components.",
                "Software Engineer",
                "Technical",
                AssessmentType.CODING_CHALLENGE,
                ProgrammingLanguage.JAVA,
                60,
                100,
                30
        );

        AssessmentSection knowledge = section(
                "knowledge-section",
                "Technical fundamentals",
                "Quick checks for fundamentals and reasoning.",
                20
        );
        knowledge.setQuestions(List.of(
                multipleChoice(
                        "q-complexity",
                        "Complexity check",
                        "Which Big-O complexity best describes a single pass over an array of n items?",
                        10,
                        List.of("O(1)", "O(log n)", "O(n)", "O(n^2)"),
                        2
                ),
                shortAnswer(
                        "q-design",
                        "Design tradeoff",
                        "How would you decide between adding a cache and optimizing the database query directly?",
                        "Look for measurement-first reasoning, invalidation awareness, and operational tradeoffs.",
                        20
                )
        ));

        AssessmentSection coding = section(
                "coding-section",
                "Practical coding",
                "Executable programming task.",
                40
        );
        AssessmentQuestion codingQuestion = codingQuestion();
        codingQuestion.setPoints(70);
        coding.setQuestions(List.of(codingQuestion));
        template.setSections(List.of(knowledge, coding));
        return template;
    }

    private AssessmentTemplate baseTemplate(
            String code,
            String name,
            String description,
            String roleTitle,
            String category,
            AssessmentType type,
            ProgrammingLanguage language,
            int durationMinutes,
            int maxScore,
            int displayOrder
    ) {
        AssessmentTemplate template = new AssessmentTemplate();
        template.setCode(code);
        template.setName(name);
        template.setDescription(description);
        template.setRoleTitle(roleTitle);
        template.setCategory(category);
        template.setType(type);
        template.setLanguage(language);
        template.setDurationMinutes(durationMinutes);
        template.setMaxScore(maxScore);
        template.setDisplayOrder(displayOrder);
        template.setActive(true);
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }

    private AssessmentSection section(String id, String title, String description, Integer timeLimitMinutes) {
        AssessmentSection section = new AssessmentSection();
        section.setId(id);
        section.setTitle(title);
        section.setDescription(description);
        section.setTimeLimitMinutes(timeLimitMinutes);
        return section;
    }

    private AssessmentQuestion shortAnswer(
            String id,
            String title,
            String prompt,
            String reviewerNotes,
            int points
    ) {
        AssessmentQuestion question = question(id, QuestionType.SHORT_ANSWER, title, prompt, points);
        question.setCorrectAnswer(reviewerNotes);
        question.setLanguage(ProgrammingLanguage.TEXT);
        return question;
    }

    private AssessmentQuestion multipleChoice(
            String id,
            String title,
            String prompt,
            int points,
            List<String> options,
            int correctIndex
    ) {
        AssessmentQuestion question = question(id, QuestionType.MULTIPLE_CHOICE, title, prompt, points);
        List<AssessmentQuestionOption> choices = new ArrayList<>();

        for (int index = 0; index < options.size(); index++) {
            AssessmentQuestionOption option = new AssessmentQuestionOption();
            option.setId(id + "-option-" + (index + 1));
            option.setText(options.get(index));
            option.setCorrect(index == correctIndex);
            choices.add(option);
        }

        question.setOptions(choices);
        question.setLanguage(ProgrammingLanguage.TEXT);
        return question;
    }

    private AssessmentQuestion codingQuestion() {
        AssessmentQuestion question = question(
                "q-two-sum",
                QuestionType.CODING_CHALLENGE,
                "Find two numbers",
                "Write a function that returns the indices of two numbers in the array that add up to the target. Return the indices separated by a comma.",
                100
        );
        question.setLanguage(ProgrammingLanguage.JAVA);
        question.setStarterCode("""
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner scanner = new Scanner(System.in);
                        String[] rawNumbers = scanner.nextLine().split(",");
                        int target = Integer.parseInt(scanner.nextLine().trim());
                        int[] numbers = new int[rawNumbers.length];

                        for (int i = 0; i < rawNumbers.length; i++) {
                            numbers[i] = Integer.parseInt(rawNumbers[i].trim());
                        }

                        int[] result = twoSum(numbers, target);
                        System.out.println(result[0] + "," + result[1]);
                    }

                    static int[] twoSum(int[] numbers, int target) {
                        return new int[] {0, 1};
                    }
                }
                """);
        question.setExpectedOutput("0,1");
        question.setTestCases(List.of(
                new AssessmentTestCase("Visible sample", "2,7,11,15\n9", "0,1", false, 30),
                new AssessmentTestCase("Hidden duplicate case", "3,3\n6", "0,1", true, 35),
                new AssessmentTestCase("Hidden later pair", "1,5,8,10\n18", "2,3", true, 35)
        ));
        return question;
    }

    private AssessmentQuestion question(
            String id,
            QuestionType type,
            String title,
            String prompt,
            int points
    ) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setType(type);
        question.setTitle(title);
        question.setPrompt(prompt);
        question.setPoints(points);
        return question;
    }
}
