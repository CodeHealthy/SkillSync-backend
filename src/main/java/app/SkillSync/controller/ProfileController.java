package app.SkillSync.controller;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.ChangePasswordRequest;
import app.SkillSync.dto.UpdateProfileRequest;
import app.SkillSync.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<AuthResponse> getCurrentProfile(Authentication authentication) {
        return ResponseEntity.ok(profileService.getCurrentProfile(authentication));
    }

    @PatchMapping
    public ResponseEntity<AuthResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.updateProfile(authentication, request));
    }

    @PatchMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        profileService.changePassword(authentication, request);

        return ResponseEntity.ok(
                Map.of("message", "Password updated successfully.")
        );
    }
}