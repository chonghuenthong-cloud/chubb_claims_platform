package com.chubb.claims.user;

import com.chubb.claims.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Works out who is calling and checks their role.
 *
 * TEMPORARY STUB: the caller is identified by the {@value #USER_HEADER} header, which anyone can set.
 * This keeps the role rules in place until JWT sign-in replaces the header; only this class changes then.
 */
@Service
public class CurrentUserService {

    public static final String USER_HEADER = "X-User-Id";

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Returns the calling user, whatever their role.
     *
     * @throws ApiException 401 if the header is missing or names no user
     */
    private AppUser require(UUID userId) {
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing " + USER_HEADER + " header");
        }
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    /**
     * Returns the calling user if they have the required role.
     *
     * @throws ApiException 401 if the header is missing or names no user; 403 if the role is wrong
     */
    public AppUser require(UUID userId, Role role) {
        AppUser user = require(userId);
        if (user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This action requires the " + role + " role");
        }
        return user;
    }
}
