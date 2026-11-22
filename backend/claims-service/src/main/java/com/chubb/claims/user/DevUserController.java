package com.chubb.claims.user;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * DEV ONLY: lists the seeded users for the UI's "Viewing as" picker.
 * Exists only under the local profile, and goes away when JWT sign-in replaces the user header.
 */
@RestController
@RequestMapping("/api/dev/users")
@Profile("local")
public class DevUserController {

    /** Just enough to show a name and pick the right view. */
    public record UserResponse(UUID id, String fullName, Role role) {
    }

    private final AppUserRepository users;

    public DevUserController(AppUserRepository users) {
        this.users = users;
    }

    /** Claimants first, then staff; alphabetical within each role. */
    @GetMapping
    public List<UserResponse> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getRole).thenComparing(AppUser::getFullName))
                .map(u -> new UserResponse(u.getId(), u.getFullName(), u.getRole()))
                .toList();
    }
}
