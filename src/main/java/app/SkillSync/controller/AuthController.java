package app.SkillSync.controller;

import app.SkillSync.dto.AcceptCandidateInviteRequest;
import app.SkillSync.dto.AcceptTeamInviteRequest;
import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.CandidateInvitePreviewResponse;
import app.SkillSync.dto.TeamInvitePreviewResponse;
import app.SkillSync.dto.ForgotPasswordRequest;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.dto.ResendVerificationRequest;
import app.SkillSync.dto.ResetPasswordRequest;
import app.SkillSync.service.AuthRateLimitService;
import app.SkillSync.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;

    public AuthController(
            AuthService authService,
            AuthRateLimitService authRateLimitService
    ) {
        this.authService = authService;
        this.authRateLimitService = authRateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String rateLimitKey = buildRateLimitKey(httpRequest, request.getEmail());

        authRateLimitService.checkRegisterAllowed(rateLimitKey);

        authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message",
                        "Account created. Please check your email to verify your account. If you do not receive it, use resend verification."
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String rateLimitKey = buildRateLimitKey(httpRequest, request.getEmail());

        authRateLimitService.checkLoginAllowed(rateLimitKey);

        AuthResponse response = authService.login(request);

        authRateLimitService.resetLoginAttempts(rateLimitKey);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);

        return ResponseEntity.ok(
                Map.of("message", "Email verified successfully. You can now log in.")
        );
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        authService.resendVerification(request);

        return ResponseEntity.ok(
                Map.of("message", "Verification email sent.")
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);

        return ResponseEntity.ok(
                Map.of("message", "If an account exists for that email, a password reset link has been sent.")
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);

        return ResponseEntity.ok(
                Map.of("message", "Password reset successfully. You can now log in.")
        );
    }

    @GetMapping("/invite")
    public ResponseEntity<CandidateInvitePreviewResponse> getCandidateInvite(
            @RequestParam String token
    ) {
        return ResponseEntity.ok(authService.getCandidateInvite(token));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptCandidateInvite(
            @Valid @RequestBody AcceptCandidateInviteRequest request
    ) {
        return ResponseEntity.ok(authService.acceptCandidateInvite(request));
    }

    @GetMapping("/team-invite")
    public ResponseEntity<TeamInvitePreviewResponse> getTeamInvite(
            @RequestParam String token,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(authService.getTeamInvite(token, name));
    }

    @PostMapping("/accept-team-invite")
    public ResponseEntity<AuthResponse> acceptTeamInvite(
            @Valid @RequestBody AcceptTeamInviteRequest request
    ) {
        return ResponseEntity.ok(authService.acceptTeamInvite(request));
    }

    private String buildRateLimitKey(HttpServletRequest request, String email) {
        String clientIp = getClientIp(request);
        String normalizedEmail = email == null ? "unknown" : email.trim().toLowerCase();

        return clientIp + ":" + normalizedEmail;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
