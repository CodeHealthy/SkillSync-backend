package app.SkillSync.controller;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.ChangePasswordRequest;
import app.SkillSync.dto.UpdateProfileRequest;
import app.SkillSync.security.AuthCookieService;
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
    private final AuthCookieService authCookieService;

    public ProfileController(ProfileService profileService, AuthCookieService authCookieService) {
        this.profileService = profileService;
        this.authCookieService = authCookieService;
    }

    @GetMapping
    public ResponseEntity<AuthResponse> getCurrentProfile(Authentication authentication) {
        return authResponse(profileService.getCurrentProfile(authentication));
    }

    @PatchMapping
    public ResponseEntity<AuthResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return authResponse(profileService.updateProfile(authentication, request));
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

    private ResponseEntity<AuthResponse> authResponse(AuthResponse response) {
        return ResponseEntity
                .ok()
                .headers(authCookieService.authCookieHeaders(response.getToken()))
                .body(response);
    }
}
