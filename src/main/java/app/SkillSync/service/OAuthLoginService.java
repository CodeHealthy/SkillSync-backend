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
public class OAuthLoginService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailTokenService emailTokenService;

    public OAuthLoginService(
            UserRepository userRepository,
            CandidateRepository candidateRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailTokenService emailTokenService
    ) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailTokenService = emailTokenService;
    }

    public User processGoogleLogin(OAuth2User oauthUser) {
        String email = extractEmail(oauthUser);
        String fullName = extractFullName(oauthUser, email);
        boolean googleEmailVerified = extractEmailVerified(oauthUser);

        User user = userRepository.findByEmail(email)
                .map(existingUser ->
                        processExistingUser(existingUser, fullName, googleEmailVerified)
                )
                .orElseGet(() -> createCandidateUser(email, fullName, googleEmailVerified));

        if (user.getRole() == Role.CANDIDATE) {
            linkCandidateProfiles(user);
        }

        return user;
    }

    public String generateToken(User user) {
        return jwtService.generateToken(user);
    }

    public String createExchangeCode(User user) {
        return emailTokenService.createOAuthExchangeToken(user);
    }

    private User processExistingUser(
            User user,
            String fullName,
            boolean googleEmailVerified
    ) {
        boolean changed = false;

        if ((user.getFullName() == null || user.getFullName().isBlank())
                && fullName != null
                && !fullName.isBlank()) {
            user.setFullName(fullName);
            changed = true;
        }

        if (googleEmailVerified && !user.isEmailVerifiedForLogin()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            changed = true;
        }

        if (user.getRole() != null && user.getRole().isOrganizationStaff()) {
            if (!user.isEmailVerifiedForLogin() && !googleEmailVerified) {
                throw new IllegalArgumentException(
                        "Google could not confirm this organization account email as verified."
                );
            }

            return changed ? userRepository.save(user) : user;
        }

        if (user.getRole() == Role.CANDIDATE) {
            return changed ? userRepository.save(user) : user;
        }

        throw new IllegalArgumentException("Unsupported user role for Google login.");
    }

    private User createCandidateUser(
            String email,
            String fullName,
            boolean googleEmailVerified
    ) {
        if (!googleEmailVerified) {
            throw new IllegalArgumentException(
                    "Google account email must be verified before signing in."
            );
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OAUTH2_GOOGLE_" + System.nanoTime()));
        user.setRole(Role.CANDIDATE);
        user.setOrganizationId(null);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setActive(true);

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

    private boolean extractEmailVerified(OAuth2User oauthUser) {
        Object emailVerifiedAttribute = oauthUser.getAttribute("email_verified");

        if (emailVerifiedAttribute instanceof Boolean verified) {
            return verified;
        }

        if (emailVerifiedAttribute != null) {
            return Boolean.parseBoolean(emailVerifiedAttribute.toString());
        }

        return false;
    }
}
