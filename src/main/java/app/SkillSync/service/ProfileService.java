package app.SkillSync.service;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.ChangePasswordRequest;
import app.SkillSync.dto.UpdateProfileRequest;
import app.SkillSync.model.Organization;
import app.SkillSync.model.User;
import app.SkillSync.repository.OrganizationRepository;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.AuthCookieService;
import app.SkillSync.util.ImageValueValidator;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthCookieService authCookieService;

    public ProfileService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            AuthCookieService authCookieService
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authCookieService = authCookieService;
    }

    public AuthResponse getCurrentProfile(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return buildAuthResponse(user);
    }

    public AuthResponse updateProfile(
            Authentication authentication,
            UpdateProfileRequest request
    ) {
        User user = getCurrentUser(authentication);

        user.setFullName(request.getFullName().trim());
        user.setProfileImageUrl(ImageValueValidator.normalizeOptionalImageValue(
                request.getProfileImageUrl(),
                "Profile image"
        ));

        if (canUpdateOrganizationLogo(user)) {
            Organization organization = organizationRepository.findById(user.getOrganizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Organization not found."));
            organization.setLogoUrl(ImageValueValidator.normalizeOptionalImageValue(
                    request.getOrganizationLogoUrl(),
                    "Organization image"
            ));
            organizationRepository.save(organization);
        }

        User savedUser = userRepository.save(user);

        return buildAuthResponse(savedUser);
    }

    public void changePassword(
            Authentication authentication,
            ChangePasswordRequest request
    ) {
        User user = getCurrentUser(authentication);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = authCookieService.createToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getOrganizationId(),
                user.getProfileImageUrl(),
                resolveOrganizationLogoUrl(user),
                requiresOrganizationSetup(user)
        );
    }

    private boolean requiresOrganizationSetup(User user) {
        return user != null
                && user.getRole() != null
                && user.getRole().isOrganizationAdmin()
                && (user.getOrganizationId() == null || user.getOrganizationId().isBlank());
    }

    private boolean canUpdateOrganizationLogo(User user) {
        return user != null
                && user.getRole() != null
                && user.getRole().isOrganizationStaff()
                && user.getOrganizationId() != null
                && !user.getOrganizationId().isBlank();
    }

    private String resolveOrganizationLogoUrl(User user) {
        if (!canUpdateOrganizationLogo(user)) {
            return null;
        }

        return organizationRepository.findById(user.getOrganizationId())
                .map(Organization::getLogoUrl)
                .orElse(null);
    }

}
