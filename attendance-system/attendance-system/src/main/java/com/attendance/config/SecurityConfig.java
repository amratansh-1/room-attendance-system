package com.attendance.config;

import com.attendance.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what:
 *   /  and /index.html -> everyone (the web page; it shows its own login form)
 *   /api/admin/**      -> ADMIN only
 *   /api/observer/**   -> OBSERVER only
 *   everything else    -> any logged-in user
 * Authentication is HTTP Basic against the users table (BCrypt hashes), stateless.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/observer/**").hasRole("OBSERVER")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    /**
     * Answers 401 when login is missing or wrong. The "WWW-Authenticate" header is what makes a
     * browser pop up its own grey login box, so it is sent only for plain browser visits, not for
     * calls made by our web page (which sends X-Requested-With and has its own login form).
     */
    private AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Attendance\"");
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> {
            UserDetails details = users.findByUsername(username)
                    .map(u -> org.springframework.security.core.userdetails.User
                            .withUsername(u.getUsername())
                            .password(u.getPasswordHash())
                            .roles(u.getRole().name())
                            .disabled(!u.isActive())
                            .build())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return details;
        };
    }
}
