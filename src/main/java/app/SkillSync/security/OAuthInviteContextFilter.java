package app.SkillSync.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class OAuthInviteContextFilter extends OncePerRequestFilter {

    public static final String FLOW_SESSION_ATTRIBUTE = "skillsync.oauth.flow";
    public static final String INVITE_TOKEN_SESSION_ATTRIBUTE = "skillsync.oauth.inviteToken";

    private static final String FLOW_REQUEST_PARAM = "skillsync_oauth_flow";
    private static final String INVITE_TOKEN_REQUEST_PARAM = "skillsync_invite_token";
    private static final Set<String> INVITE_FLOWS = Set.of("candidate-invite", "team-invite");
    private static final Set<String> ALLOWED_FLOWS = Set.of("public", "candidate-invite", "team-invite");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isGoogleAuthorizationRequest(request)) {
            captureOAuthContext(request);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isGoogleAuthorizationRequest(HttpServletRequest request) {
        return "/oauth2/authorization/google".equals(request.getRequestURI());
    }

    private void captureOAuthContext(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String flow = trimToNull(request.getParameter(FLOW_REQUEST_PARAM));
        String inviteToken = trimToNull(request.getParameter(INVITE_TOKEN_REQUEST_PARAM));

        if (flow == null || !ALLOWED_FLOWS.contains(flow)) {
            clearOAuthContext(session);
            return;
        }

        if (INVITE_FLOWS.contains(flow) && inviteToken == null) {
            clearOAuthContext(session);
            return;
        }

        session.setAttribute(FLOW_SESSION_ATTRIBUTE, flow);

        if (inviteToken == null) {
            session.removeAttribute(INVITE_TOKEN_SESSION_ATTRIBUTE);
        } else {
            session.setAttribute(INVITE_TOKEN_SESSION_ATTRIBUTE, inviteToken);
        }
    }

    private void clearOAuthContext(HttpSession session) {
        session.removeAttribute(FLOW_SESSION_ATTRIBUTE);
        session.removeAttribute(INVITE_TOKEN_SESSION_ATTRIBUTE);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        return value.trim();
    }
}
