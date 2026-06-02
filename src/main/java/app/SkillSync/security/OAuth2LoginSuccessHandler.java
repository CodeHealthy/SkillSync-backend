package app.SkillSync.security;

import app.SkillSync.model.User;
import app.SkillSync.service.OAuthLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthLoginService oAuthLoginService;

    @Value("${app.oauth.frontend-success-url}")
    private String frontendSuccessUrl;

    @Value("${app.oauth.frontend-failure-url}")
    private String frontendFailureUrl;

    public OAuth2LoginSuccessHandler(OAuthLoginService oAuthLoginService) {
        this.oAuthLoginService = oAuthLoginService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            OAuthContext context = consumeOAuthContext(request);

            User user = oAuthLoginService.processGoogleLogin(
                    oauthUser,
                    context.flow(),
                    context.inviteToken()
            );
            String exchangeCode = oAuthLoginService.createExchangeCode(user);

            String redirectUrl = UriComponentsBuilder
                    .fromUriString(frontendSuccessUrl)
                    .queryParam("code", exchangeCode)
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

    private OAuthContext consumeOAuthContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return new OAuthContext(null, null);
        }

        String flow = (String) session.getAttribute(
                OAuthInviteContextFilter.FLOW_SESSION_ATTRIBUTE
        );
        String inviteToken = (String) session.getAttribute(
                OAuthInviteContextFilter.INVITE_TOKEN_SESSION_ATTRIBUTE
        );

        session.removeAttribute(OAuthInviteContextFilter.FLOW_SESSION_ATTRIBUTE);
        session.removeAttribute(OAuthInviteContextFilter.INVITE_TOKEN_SESSION_ATTRIBUTE);

        return new OAuthContext(flow, inviteToken);
    }

    private record OAuthContext(String flow, String inviteToken) {
    }
}
