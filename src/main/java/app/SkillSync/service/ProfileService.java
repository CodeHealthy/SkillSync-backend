package app.SkillSync.service;

import app.SkillSync.dto.AuthResponse;
import app.SkillSync.dto.ChangePasswordRequest;
import app.SkillSync.dto.UpdateProfileRequest;
import app.SkillSync.model.User;
import app.SkillSync.repository.UserRepository;
import app.SkillSync.security.AuthCookieService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthCookieService authCookieService;

    public ProfileService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthCookieService authCookieService
    ) {
        this.userRepository = userRepository;
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
                user.getRole()
        );
    }
}
