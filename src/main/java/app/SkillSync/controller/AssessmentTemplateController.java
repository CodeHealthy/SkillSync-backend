package app.SkillSync.controller;

import app.SkillSync.model.AssessmentTemplate;
import app.SkillSync.service.AssessmentTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assessment-templates")
public class AssessmentTemplateController {

    private final AssessmentTemplateService templateService;

    public AssessmentTemplateController(AssessmentTemplateService templateService) {
        this.templateService = templateService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER')")
    @GetMapping
    public ResponseEntity<List<AssessmentTemplate>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplatesForCurrentUser());
    }
}
