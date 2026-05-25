package app.SkillSync.controller;

import app.SkillSync.dto.AuditLogResponse;
import app.SkillSync.dto.PlatformAdminSummaryResponse;
import app.SkillSync.service.AuditLogService;
import app.SkillSync.service.PlatformAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final AuditLogService auditLogService;

    public PlatformAdminController(
            PlatformAdminService platformAdminService,
            AuditLogService auditLogService
    ) {
        this.platformAdminService = platformAdminService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PlatformAdminSummaryResponse> getSummary(
            Authentication authentication
    ) {
        return ResponseEntity.ok(platformAdminService.getSummary(authentication));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> listPlatformLogs(
            Authentication authentication,
            @RequestParam(required = false) String action
    ) {
        return ResponseEntity.ok(
                auditLogService.listPlatformLogs(authentication, action)
        );
    }
}
