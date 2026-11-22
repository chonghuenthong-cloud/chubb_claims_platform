package com.chubb.claims.claim;

import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.CurrentUserService;
import com.chubb.claims.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Claim endpoints. Each one checks the caller's role first, then hands off to ClaimService. */
@RestController
public class ClaimController {

    private final ClaimService claimService;
    private final CurrentUserService currentUser;

    public ClaimController(ClaimService claimService, CurrentUserService currentUser) {
        this.claimService = claimService;
        this.currentUser = currentUser;
    }

    /** Claimant reports an incident. Returns 201 with the new claim in SUBMITTED status. */
    @PostMapping("/api/claims")
    public ResponseEntity<ClaimSummaryResponse> submit(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @Valid @RequestBody SubmitClaimRequest request) {
        AppUser claimant = currentUser.require(userId, Role.CLAIMANT);
        ClaimSummaryResponse created = claimService.submit(claimant, request);
        return ResponseEntity.created(URI.create("/api/claims/" + created.id())).body(created);
    }

    /** One claim with description and status history. Staff: any claim. Claimant: own claims only (else 404). */
    @GetMapping("/api/claims/{id}")
    public ClaimDetailResponse detail(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id) {
        return claimService.detail(currentUser.require(userId), id);
    }

    /** Claimant's own claims, newest first. */
    @GetMapping("/api/me/claims")
    public List<ClaimSummaryResponse> myClaims(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        return claimService.claimsOf(currentUser.require(userId, Role.CLAIMANT));
    }

    /** Staff work queue: every claim, newest first. */
    @GetMapping("/api/claims")
    public List<ClaimSummaryResponse> allClaims(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        currentUser.require(userId, Role.STAFF);
        return claimService.allClaims();
    }
}
