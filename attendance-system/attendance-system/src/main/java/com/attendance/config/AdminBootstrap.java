package com.attendance.config;

import com.attendance.domain.AppUser;
import com.attendance.domain.Role;
import com.attendance.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The very first admin has to come from somewhere, because only an admin can create users.
 * On startup, if no ADMIN exists, one is created from application.properties.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String fullName;

    public AdminBootstrap(AppUserRepository users,
                          PasswordEncoder encoder,
                          @Value("${app.admin.username}") String username,
                          @Value("${app.admin.password}") String password,
                          @Value("${app.admin.full-name:System Administrator}") String fullName) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(String... args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setFullName(fullName);
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        users.save(admin);
        log.warn("No admin existed, so '{}' was created. Change its password before real use.", username);
    }
}
