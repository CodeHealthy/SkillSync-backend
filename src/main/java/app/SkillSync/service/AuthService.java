package app.SkillSync.service;
import app.SkillSync.model.Candidate;
import app.SkillSync.model.Organization;
import app.SkillSync.repository.CandidateRepository;

import java.util.List;
import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.LoginRequest;
import app.SkillSync.dto.RegisterRequest;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

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
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.CANDIDATE;

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setCreatedAt(Instant.now());

        if (role == Role.ADMIN) {
            if (request.getOrganizationName() == null || request.getOrganizationName().trim().isBlank()) {
                throw new IllegalArgumentException("Organization name is required for admin registration.");
            }

            Organization organization = new Organization();
            organization.setName(request.getOrganizationName().trim());
            organization.setCreatedAt(Instant.now());

            Organization savedOrganization = organizationRepository.save(organization);
            user.setOrganizationId(savedOrganization.getId());
        }

        User savedUser = userRepository.save(user);

        if (savedUser.getRole() == Role.CANDIDATE) {
            List<Candidate> matchingCandidateProfiles =
                    candidateRepository.findAllByEmailIgnoreCase(normalizedEmail);

            for (Candidate candidate : matchingCandidateProfiles) {
                candidate.setUserId(savedUser.getId());
                candidate.setStatus("REGISTERED");
                candidateRepository.save(candidate);
            }
        }

        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }
}