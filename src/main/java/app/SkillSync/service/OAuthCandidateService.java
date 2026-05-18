package app.SkillSync.service;

import app.SkillSync.model.Candidate;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.CandidateRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OAuthCandidateService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public OAuthCandidateService(
            UserRepository userRepository,
            CandidateRepository candidateRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User processGoogleCandidate(OAuth2User oauthUser) {
        String email = extractEmail(oauthUser);
        String fullName = extractFullName(oauthUser, email);

        User user = userRepository.findByEmail(email)
                .map(existingUser -> validateExistingUser(existingUser, fullName))
                .orElseGet(() -> createCandidateUser(email, fullName));

        linkCandidateProfiles(user);

        return user;
    }

    public String generateToken(User user) {
        return jwtService.generateToken(user);
    }

    private User validateExistingUser(User user, String fullName) {
        if (user.getRole() != Role.CANDIDATE) {
            throw new IllegalArgumentException(
                    "Google login is currently available for candidate accounts only."
            );
        }
        if (!user.isEmailVerifiedForLogin()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            return userRepository.save(user);
        }
        if ((user.getFullName() == null || user.getFullName().isBlank())
                && fullName != null
                && !fullName.isBlank()) {
            user.setFullName(fullName);
            return userRepository.save(user);
        }

        return user;
    }

    private User createCandidateUser(String email, String fullName) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OAUTH2_GOOGLE_" + System.nanoTime()));
        user.setRole(Role.CANDIDATE);
        user.setOrganizationId(null);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());

        return userRepository.save(user);
    }

    private void linkCandidateProfiles(User user) {
        List<Candidate> matchingCandidateProfiles =
                candidateRepository.findAllByEmailIgnoreCase(user.getEmail());

        for (Candidate candidate : matchingCandidateProfiles) {
            candidate.setUserId(user.getId());
            candidate.setStatus("REGISTERED");
            candidateRepository.save(candidate);
        }
    }

    private String extractEmail(OAuth2User oauthUser) {
        Object emailAttribute = oauthUser.getAttribute("email");

        if (emailAttribute == null || emailAttribute.toString().trim().isBlank()) {
            throw new IllegalArgumentException("Google account did not provide an email address.");
        }

        return emailAttribute.toString().trim().toLowerCase();
    }

    private String extractFullName(OAuth2User oauthUser, String email) {
        Object nameAttribute = oauthUser.getAttribute("name");

        if (nameAttribute != null && !nameAttribute.toString().trim().isBlank()) {
            return nameAttribute.toString().trim();
        }

        return email;
    }
}