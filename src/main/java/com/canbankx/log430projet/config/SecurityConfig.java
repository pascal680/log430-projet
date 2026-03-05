package com.canbankx.log430projet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Main security filter chain.
     * - CSRF disabled     → POST/PUT/DELETE work in Postman without a CSRF token
     * - Stateless session → no server-side session, fits REST APIs
     * - HTTP Basic Auth   → Postman sends credentials via Authorization header
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless: no HttpSession created or used
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Actuator health/info publicly accessible
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Internal service routes called by KrakenD — no auth needed at this layer
                .requestMatchers("/accountservice/**").permitAll()
                // Public-facing gateway routes require auth
                .requestMatchers("/api/**").authenticated()
                // Everything else denied by default
                .anyRequest().denyAll()
            )

            // HTTP Basic Auth → works natively in Postman (Authorization tab → Basic Auth)
            .httpBasic(basic -> {});

        return http.build();
    }

    /**
     * In-memory user for Postman testing.
     * Credentials are loaded from application.yaml (spring.security.user).
     * Replace with a DB-backed UserDetailsService when ready for production.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
