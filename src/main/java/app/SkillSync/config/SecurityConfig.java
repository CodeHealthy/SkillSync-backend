package app.SkillSync.config;

import app.SkillSync.security.JwtAuthenticationFilter;
import app.SkillSync.security.CsrfCookieFilter;
import app.SkillSync.security.OAuth2LoginFailureHandler;
import app.SkillSync.security.OAuth2LoginSuccessHandler;
import app.SkillSync.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CsrfCookieFilter csrfCookieFilter,
            CustomUserDetailsService customUserDetailsService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            PasswordEncoder passwordEncoder
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.csrfCookieFilter = csrfCookieFilter;
        this.customUserDetailsService = customUserDetailsService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oAuth2LoginFailureHandler = oAuth2LoginFailureHandler;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/");

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers("/api/billing/webhook")
                )
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/health", "/api/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/billing/plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/billing/webhook").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterAfter(csrfCookieFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
