package app.SkillSync.service;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Organization;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            CandidateRepository candidateRepository,
            OrganizationRepository organizationRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.candidateRepository = candidateRepository;
        this.organizationRepository = organizationRepository;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedFullName = normalizeRequiredText(request.getFullName(), "Full name is required");

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.CANDIDATE;

        User user = new User();
        user.setFullName(normalizedFullName);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setCreatedAt(Instant.now());

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

        if (savedUser.getRole() == Role.CANDIDATE) {
            linkCandidateProfiles(savedUser);
        }

        return buildAuthResponse(savedUser);
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
        } catch (AuthenticationException ex) {
            throw new IllegalArgumentException(INVALID_LOGIN_MESSAGE);
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_LOGIN_MESSAGE));

        return buildAuthResponse(user);
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