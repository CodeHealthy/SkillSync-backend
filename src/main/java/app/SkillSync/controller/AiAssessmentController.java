package app.SkillSync.controller;


import app.SkillSync.dto.AiAssessmentRequest;
import app.SkillSync.dto.AiAssessmentResponse;
import app.SkillSync.service.AiAssessmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/assessments")
public class AiAssessmentController {

    private final AiAssessmentService aiAssessmentService;

    public AiAssessmentController(AiAssessmentService aiAssessmentService) {
        this.aiAssessmentService = aiAssessmentService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public AiAssessmentResponse generateAssessment(@Valid @RequestBody AiAssessmentRequest request) {
        return aiAssessmentService.generateAssessment(request);
    }
}