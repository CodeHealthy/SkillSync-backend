package app.SkillSync.controller;

import app.SkillSync.dto.AuditLogResponse;
import app.SkillSync.dto.PlatformAdminSummaryResponse;
import app.SkillSync.dto.PlatformOrganizationResponse;
import app.SkillSync.dto.PlatformOrganizationUpdateRequest;
import app.SkillSync.dto.PlatformSubscriptionPlanRequest;
import app.SkillSync.dto.PlatformUserResponse;
import app.SkillSync.dto.PlatformUserUpdateRequest;
import app.SkillSync.dto.SubscriptionPlanResponse;
import app.SkillSync.service.AuditLogService;
import app.SkillSync.service.PlatformAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

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
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String targetType
    ) {
        return ResponseEntity.ok(
                auditLogService.listPlatformLogs(
                        authentication,
                        action,
                        organizationId,
                        actorEmail,
                        targetType
                )
        );
    }

    @GetMapping("/subscription-plans")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SubscriptionPlanResponse>> listSubscriptionPlans(
            Authentication authentication
    ) {
        return ResponseEntity.ok(platformAdminService.listSubscriptionPlans(authentication));
    }

    @PostMapping("/subscription-plans")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SubscriptionPlanResponse> createSubscriptionPlan(
            Authentication authentication,
            @Valid @RequestBody PlatformSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(platformAdminService.createSubscriptionPlan(authentication, request));
    }

    @PatchMapping("/subscription-plans/{planId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SubscriptionPlanResponse> updateSubscriptionPlan(
            Authentication authentication,
            @PathVariable String planId,
            @Valid @RequestBody PlatformSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(platformAdminService.updateSubscriptionPlan(authentication, planId, request));
    }

    @PostMapping("/subscription-plans/{planId}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SubscriptionPlanResponse> deactivateSubscriptionPlan(
            Authentication authentication,
            @PathVariable String planId
    ) {
        return ResponseEntity.ok(platformAdminService.deactivateSubscriptionPlan(authentication, planId));
    }

    @PatchMapping("/organizations/{organizationId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PlatformOrganizationResponse> updateOrganization(
            Authentication authentication,
            @PathVariable String organizationId,
            @Valid @RequestBody PlatformOrganizationUpdateRequest request
    ) {
        return ResponseEntity.ok(
                platformAdminService.updateOrganization(authentication, organizationId, request)
        );
    }

    @PatchMapping("/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PlatformUserResponse> updateUser(
            Authentication authentication,
            @PathVariable String userId,
            @RequestBody PlatformUserUpdateRequest request
    ) {
        return ResponseEntity.ok(platformAdminService.updateUser(authentication, userId, request));
    }
}
