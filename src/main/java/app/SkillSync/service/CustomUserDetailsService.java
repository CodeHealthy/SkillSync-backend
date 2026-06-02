package app.SkillSync.service;

import app.SkillSync.model.User;
import app.SkillSync.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;

    public CustomUserDetailsService(
            UserRepository userRepository,
            OrganizationAccessService organizationAccessService
    ) {
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (!user.isActiveForLogin()) {
            throw new UsernameNotFoundException("User is deactivated: " + email);
        }

        try {
            organizationAccessService.requireActiveOrganizationForUser(user);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException(exception.getMessage(), exception);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
