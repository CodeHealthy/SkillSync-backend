package app.SkillSync.controller;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.service.AuthRateLimitService;
import app.SkillSync.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String rateLimitKey = buildRateLimitKey(httpRequest, request.getEmail());

        authRateLimitService.checkRegisterAllowed(rateLimitKey);

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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