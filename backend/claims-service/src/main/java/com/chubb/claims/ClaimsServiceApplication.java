package com.chubb.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the claims platform backend.
 * Packages are split by domain module (claim, policy, user), so each can be extracted into its own service later.
 */
@SpringBootApplication
public class ClaimsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsServiceApplication.class, args);
    }
}
