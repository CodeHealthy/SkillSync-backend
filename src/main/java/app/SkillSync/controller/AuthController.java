package app.SkillSync.controller;

import app.SkillSync.dto.AcceptCandidateInviteRequest;
import app.SkillSync.dto.AcceptTeamInviteRequest;
import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.CandidateInvitePreviewResponse;
import app.SkillSync.dto.TeamInvitePreviewResponse;
import app.SkillSync.dto.ForgotPasswordRequest;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.OAuthExchangeRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.dto.ResendVerificationRequest;
import app.SkillSync.dto.ResetPasswordRequest;
import app.SkillSync.security.AuthCookieService;
import app.SkillSync.service.AuthRateLimitService;
import app.SkillSync.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;
    private final AuthCookieService authCookieService;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureCookie;

    public AuthController(
            AuthService authService,
            AuthRateLimitService authRateLimitService,
            AuthCookieService authCookieService
    ) {
        this.authService = authService;
        this.authRateLimitService = authRateLimitService;
        this.authCookieService = authCookieService;
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

        return authResponse(response);
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
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimitService.checkTokenFlowAllowed(buildRateLimitKey(httpRequest, request.getEmail()));

        authService.resendVerification(request);

        return ResponseEntity.ok(
                Map.of("message", "Verification email sent.")
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimitService.checkPasswordFlowAllowed(buildRateLimitKey(httpRequest, request.getEmail()));

        authService.forgotPassword(request);

        return ResponseEntity.ok(
                Map.of("message", "If an account exists for that email, a password reset link has been sent.")
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimitService.checkPasswordFlowAllowed(buildRateLimitKey(httpRequest, request.getToken()));

        authService.resetPassword(request);

        return ResponseEntity.ok(
                Map.of("message", "Password reset successfully. You can now log in.")
        );
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponse> exchangeOAuthCode(
            @Valid @RequestBody OAuthExchangeRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimitService.checkTokenFlowAllowed(buildRateLimitKey(httpRequest, request.getCode()));

        return authResponse(authService.exchangeOAuthCode(request));
    }

    @GetMapping("/invite")
    public ResponseEntity<CandidateInvitePreviewResponse> getCandidateInvite(
            @RequestParam String token
    ) {
        return ResponseEntity.ok(authService.getCandidateInvite(token));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptCandidateInvite(
            @Valid @RequestBody AcceptCandidateInviteRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimitService.checkTokenFlowAllowed(buildRateLimitKey(httpRequest, request.getToken()));

        return authResponse(authService.acceptCandidateInvite(request));
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
            @Valid @RequestBody AcceptTeamInviteRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimitService.checkTokenFlowAllowed(buildRateLimitKey(httpRequest, request.getToken()));

        return authResponse(authService.acceptTeamInvite(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity
                .ok()
                .headers(authCookieService.clearCookieHeaders())
                .body(Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken, HttpServletResponse response) {
        ResponseCookie csrfCookie = ResponseCookie
                .from("XSRF-TOKEN", csrfToken.getToken())
                .httpOnly(false)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());

        return ResponseEntity.ok(Map.of("headerName", "X-XSRF-TOKEN"));
    }

    private ResponseEntity<AuthResponse> authResponse(AuthResponse response) {
        return ResponseEntity
                .ok()
                .headers(authCookieService.authCookieHeaders(response.getToken()))
                .body(response);
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
