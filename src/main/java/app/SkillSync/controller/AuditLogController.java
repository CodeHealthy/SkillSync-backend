package app.SkillSync.controller;

import app.SkillSync.dto.AuditLogResponse;
import app.SkillSync.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ORG_ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> listOrganizationLogs(
            Authentication authentication,
            @RequestParam(required = false) String action
    ) {
        return ResponseEntity.ok(
                auditLogService.listOrganizationLogs(authentication, action)
        );
    }
}
