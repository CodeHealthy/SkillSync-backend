package app.SkillSync.security;

import app.SkillSync.model.User;
import app.SkillSync.service.OAuthCandidateService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthCandidateService oAuthCandidateService;

    @Value("${app.oauth.frontend-success-url}")
    private String frontendSuccessUrl;

    @Value("${app.oauth.frontend-failure-url}")
    private String frontendFailureUrl;

    public OAuth2LoginSuccessHandler(OAuthCandidateService oAuthCandidateService) {
        this.oAuthCandidateService = oAuthCandidateService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

            User user = oAuthCandidateService.processGoogleCandidate(oauthUser);
            String token = oAuthCandidateService.generateToken(user);

            String redirectUrl = UriComponentsBuilder
                    .fromUriString(frontendSuccessUrl)
                    .queryParam("token", token)
                    .queryParam("userId", user.getId())
                    .queryParam("fullName", user.getFullName())
                    .queryParam("email", user.getEmail())
                    .queryParam("role", user.getRole().name())
                    .build()
                    .encode()
                    .toUriString();

            response.sendRedirect(redirectUrl);
        } catch (Exception exception) {
            String redirectUrl = UriComponentsBuilder
                    .fromUriString(frontendFailureUrl)
                    .queryParam(
                            "message",
                            exception.getMessage() != null
                                    ? exception.getMessage()
                                    : "Google login failed"
                    )
                    .build()
                    .encode()
                    .toUriString();

            response.sendRedirect(redirectUrl);
        }
    }
}