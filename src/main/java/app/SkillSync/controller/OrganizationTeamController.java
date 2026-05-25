package app.SkillSync.controller;

import app.SkillSync.dto.InviteTeamMemberRequest;
import app.SkillSync.dto.TeamInviteResponse;
import app.SkillSync.dto.TeamMemberResponse;
import app.SkillSync.service.OrganizationTeamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/team")
public class OrganizationTeamController {

    private final OrganizationTeamService organizationTeamService;

    public OrganizationTeamController(OrganizationTeamService organizationTeamService) {
        this.organizationTeamService = organizationTeamService;
    }

    @GetMapping
    public ResponseEntity<List<TeamMemberResponse>> listTeamMembers(
            Authentication authentication
    ) {
        return ResponseEntity.ok(organizationTeamService.listTeamMembers(authentication));
    }

    @GetMapping("/invites")
    public ResponseEntity<List<TeamInviteResponse>> listPendingInvites(
            Authentication authentication
    ) {
        return ResponseEntity.ok(organizationTeamService.listPendingInvites(authentication));
    }

    @PostMapping("/invites")
    public ResponseEntity<Map<String, String>> inviteTeamMember(
            Authentication authentication,
            @Valid @RequestBody InviteTeamMemberRequest request
    ) {
        return ResponseEntity.ok(
                organizationTeamService.inviteTeamMember(authentication, request)
        );
    }

    @PostMapping("/invites/{inviteId}/resend")
    public ResponseEntity<Map<String, String>> resendTeamInvite(
            Authentication authentication,
            @PathVariable String inviteId
    ) {
        return ResponseEntity.ok(
                organizationTeamService.resendTeamInvite(authentication, inviteId)
        );
    }

    @PostMapping("/invites/{inviteId}/revoke")
    public ResponseEntity<Map<String, String>> revokeTeamInvite(
            Authentication authentication,
            @PathVariable String inviteId
    ) {
        return ResponseEntity.ok(
                organizationTeamService.revokeTeamInvite(authentication, inviteId)
        );
    }

    @PatchMapping("/members/{userId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateTeamMember(
            Authentication authentication,
            @PathVariable String userId
    ) {
        return ResponseEntity.ok(
                organizationTeamService.deactivateTeamMember(authentication, userId)
        );
    }
}
