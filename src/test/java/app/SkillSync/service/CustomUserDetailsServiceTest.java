package app.SkillSync.service;

import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private OrganizationAccessService organizationAccessService;
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        organizationAccessService = mock(OrganizationAccessService.class);
        customUserDetailsService = new CustomUserDetailsService(
                userRepository,
                organizationAccessService
        );
    }

    @Test
    void loadUserAllowsPendingOrganizationAdminDuringOrganizationSetup() {
        User user = user("admin@skillsync.com", Role.ORG_ADMIN, null);
        when(userRepository.findByEmail("admin@skillsync.com")).thenReturn(Optional.of(user));

        UserDetails userDetails =
                customUserDetailsService.loadUserByUsername("admin@skillsync.com");

        assertEquals("admin@skillsync.com", userDetails.getUsername());
        assertEquals("ROLE_ORG_ADMIN", userDetails.getAuthorities().iterator().next().getAuthority());
        verify(organizationAccessService, never()).requireActiveOrganizationForUser(user);
    }

    @Test
    void loadUserRejectsOtherOrganizationStaffWithoutOrganization() {
        User user = user("recruiter@skillsync.com", Role.RECRUITER, null);
        when(userRepository.findByEmail("recruiter@skillsync.com")).thenReturn(Optional.of(user));
        doThrow(new IllegalArgumentException("Organization id is required."))
                .when(organizationAccessService)
                .requireActiveOrganizationForUser(user);

        assertThrows(
                UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("recruiter@skillsync.com")
        );
    }

    private User user(String email, Role role, String organizationId) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setRole(role);
        user.setOrganizationId(organizationId);
        user.setActive(true);
        return user;
    }
}
