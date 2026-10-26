package com.attendance.web;

import com.attendance.domain.AppUser;
import com.attendance.exception.ApiException;
import com.attendance.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the web page who just logged in (and whether they are ADMIN or OBSERVER). */
@RestController
public class MeController {

    private final AppUserRepository users;

    public MeController(AppUserRepository users) {
        this.users = users;
    }

    public record Me(String username, String fullName, String role) {
    }

    @GetMapping("/api/me")
    public Me me(Authentication auth) {
        AppUser user = users.findByUsername(auth.getName())
                .orElseThrow(() -> ApiException.forbidden("Unknown user"));
        return new Me(user.getUsername(), user.getFullName(), user.getRole().name());
    }
}
