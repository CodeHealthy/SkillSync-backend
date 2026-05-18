package app.SkillSync.service;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.ForgotPasswordRequest;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.dto.ResendVerificationRequest;
import app.SkillSync.dto.ResetPasswordRequest;
import app.SkillSync.model.AuthTokenType;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.EmailToken;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final String INVALID_LOGIN_MESSAGE = "Invalid email or password";

    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            CandidateRepository candidateRepository,
            OrganizationRepository organizationRepository,
            EmailTokenService emailTokenService,
            MailService mailService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.candidateRepository = candidateRepository;
        this.organizationRepository = organizationRepository;
        this.emailTokenService = emailTokenService;
        this.mailService = mailService;
    }

    public void register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.CANDIDATE;

        User user = new User();
        user.setFullName(normalizeRequiredText(request.getFullName(), "Full name is required"));
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);

        if (role == Role.ADMIN) {
            String organizationName = normalizeRequiredText(
                    request.getOrganizationName(),
                    "Organization name is required for admin registration."
            );

            Organization organization = new Organization();
            organization.setName(organizationName);
            organization.setCreatedAt(Instant.now());

            Organization savedOrganization = organizationRepository.save(organization);
            user.setOrganizationId(savedOrganization.getId());
        }

        User savedUser = userRepository.save(user);

        sendVerificationEmail(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new IllegalArgumentException(INVALID_LOGIN_MESSAGE);
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_LOGIN_MESSAGE));

        if (!user.isEmailVerifiedForLogin()) {
            throw new IllegalArgumentException("Please verify your email before logging in.");
        }

        return buildAuthResponse(user);
    }

    public void verifyEmail(String rawToken) {
        EmailToken token = emailTokenService.validateToken(
                rawToken,
                AuthTokenType.EMAIL_VERIFICATION
        );

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());

        User savedUser = userRepository.save(user);

        if (savedUser.getRole() == Role.CANDIDATE) {
            linkCandidateProfiles(savedUser);
        }

        emailTokenService.markUsed(token);
    }

    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account found for this email."));

        if (user.isEmailVerifiedForLogin()) {
            throw new IllegalArgumentException("This email is already verified.");
        }
        emailTokenService.enforceResendVerificationCooldown(user.getEmail());
        sendVerificationEmail(user);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmail(normalizedEmail)
                .filter(User::isEmailVerifiedForLogin)
                .ifPresent(this::sendPasswordResetEmail);
    }

    public void resetPassword(ResetPasswordRequest request) {
        EmailToken token = emailTokenService.validateToken(
                request.getToken(),
                AuthTokenType.PASSWORD_RESET
        );

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);
        emailTokenService.markUsed(token);
    }

    private void sendVerificationEmail(User user) {
        String rawToken = emailTokenService.createEmailVerificationToken(user);

        String verificationLink = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        mailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                verificationLink
        );
    }

    private void sendPasswordResetEmail(User user) {
        String rawToken = emailTokenService.createPasswordResetToken(user);

        String resetLink = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        mailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFullName(),
                resetLink
        );
    }

    private void linkCandidateProfiles(User savedUser) {
        List<Candidate> matchingCandidateProfiles =
                candidateRepository.findAllByEmailIgnoreCase(savedUser.getEmail());

        for (Candidate candidate : matchingCandidateProfiles) {
            candidate.setUserId(savedUser.getId());
            candidate.setStatus("REGISTERED");
            candidateRepository.save(candidate);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        return email.trim().toLowerCase();
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }
}