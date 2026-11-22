package com.chubb.claims.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Used at sign-in. */
    Optional<AppUser> findByEmail(String email);
}
