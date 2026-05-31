package app.SkillSync.security;

import app.SkillSync.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtCookieAuthenticationOverridesExistingSessionAuthenticationAuthorities() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        UserDetails superAdminDetails = new User(
                "super@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "oauth-session-user",
                        null,
                        List.of(new SimpleGrantedAuthority("OAUTH2_USER"))
                )
        );
        request.setCookies(new Cookie(AuthCookieService.AUTH_COOKIE_NAME, "jwt-token"));

        when(jwtService.extractEmail("jwt-token")).thenReturn("super@example.com");
        when(userDetailsService.loadUserByUsername("super@example.com"))
                .thenReturn(superAdminDetails);
        when(jwtService.isTokenValid("jwt-token", superAdminDetails)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertTrue(
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"))
        );
        verify(chain).doFilter(request, response);
    }
}
