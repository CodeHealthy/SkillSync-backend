package app.SkillSync.security;

import app.SkillSync.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    public static final String AUTH_COOKIE_NAME = "skillsync_auth";

    private final JwtService jwtService;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String sameSite;

    public AuthCookieService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public String createToken(User user) {
        return jwtService.generateToken(user);
    }

    public String createAuthCookie(String token) {
        return baseCookie(token)
                .maxAge(Math.max(jwtExpirationMs / 1000, 1))
                .build()
                .toString();
    }

    public String createClearCookie() {
        return baseCookie("")
                .maxAge(0)
                .build()
                .toString();
    }

    public HttpHeaders authCookieHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, createAuthCookie(token));
        return headers;
    }

    public HttpHeaders clearCookieHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, createClearCookie());
        return headers;
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/");
    }
}
