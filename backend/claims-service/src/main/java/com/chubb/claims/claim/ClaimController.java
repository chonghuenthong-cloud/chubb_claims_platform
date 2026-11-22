package com.chubb.claims.claim;

import com.chubb.claims.claim.ClaimActionRequests.AnswerRequest;
import com.chubb.claims.claim.ClaimActionRequests.ApproveRequest;
import com.chubb.claims.claim.ClaimActionRequests.RejectRequest;
import com.chubb.claims.claim.ClaimActionRequests.RequestInfoRequest;
import com.chubb.claims.claim.ClaimActionRequests.ReviseReserveRequest;
import com.chubb.claims.claim.ClaimService.QueueFilter;
import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.CurrentUserService;
import com.chubb.claims.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Claim endpoints. Each one checks the caller's role first, then hands off to ClaimService.
 * Actions are separate endpoints (pick-up, approve, ...) so each maps to one business step with its own rules.
 */
@RestController
public class ClaimController {

    private final ClaimService claimService;
    private final CurrentUserService currentUser;

    public ClaimController(ClaimService claimService, CurrentUserService currentUser) {
        this.claimService = claimService;
        this.currentUser = currentUser;
    }

    // --- Claimant ---

    // Claimant endpoints return claimant-facing types (ClaimantClaimSummary / ClaimantClaimDetail),
    // which leave out internal figures such as the reserve.

    /** Claimant reports an incident. Returns 201 with the new claim in SUBMITTED status. */
    @PostMapping("/api/claims")
    public ResponseEntity<ClaimantClaimSummary> submit(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @Valid @RequestBody SubmitClaimRequest request) {
        AppUser claimant = currentUser.require(userId, Role.CLAIMANT);
        ClaimantClaimSummary created = claimService.submit(claimant, request);
        return ResponseEntity.created(URI.create("/api/me/claims/" + created.id())).body(created);
    }

    /** Claimant's own claims, newest first. */
    @GetMapping("/api/me/claims")
    public List<ClaimantClaimSummary> myClaims(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        return claimService.claimsOf(currentUser.require(userId, Role.CLAIMANT));
    }

    /** Claimant's own claim: status, decision, timeline and questions. Someone else's claim is 404. */
    @GetMapping("/api/me/claims/{id}")
    public ClaimantClaimDetail myClaim(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id) {
        return claimService.claimantDetail(currentUser.require(userId, Role.CLAIMANT), id);
    }

    /** Claimant answers the open question: AWAITING_INFO -> UNDER_REVIEW. */
    @PostMapping("/api/claims/{id}/answer")
    public ClaimantClaimDetail answer(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id, @Valid @RequestBody AnswerRequest request) {
        return claimService.answerInformationRequest(currentUser.require(userId, Role.CLAIMANT), id, request.response());
    }

    // --- Staff ---

    /** Any claim in full, including the reserve. */
    @GetMapping("/api/claims/{id}")
    public ClaimDetailResponse detail(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id) {
        currentUser.require(userId, Role.STAFF);
        return claimService.staffDetail(id);
    }

    /** Staff work queue, newest first. filter: ALL (default), UNASSIGNED or MINE. */
    @GetMapping("/api/claims")
    public List<ClaimSummaryResponse> queue(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @RequestParam(defaultValue = "ALL") QueueFilter filter) {
        return claimService.queue(currentUser.require(userId, Role.STAFF), filter);
    }

    /** Officer takes an unassigned claim: SUBMITTED -> UNDER_REVIEW, assigned to the caller. */
    @PostMapping("/api/claims/{id}/pick-up")
    public ClaimDetailResponse pickUp(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id) {
        return claimService.pickUp(currentUser.require(userId, Role.STAFF), id);
    }

    /** Assigned officer revises the reserve (0 to coverage limit) while assessing. */
    @PutMapping("/api/claims/{id}/reserve")
    public ClaimDetailResponse reviseReserve(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id, @Valid @RequestBody ReviseReserveRequest request) {
        return claimService.reviseReserve(currentUser.require(userId, Role.STAFF), id, request.amount());
    }

    /** Assigned officer asks the claimant a question: UNDER_REVIEW -> AWAITING_INFO. */
    @PostMapping("/api/claims/{id}/request-info")
    public ClaimDetailResponse requestInfo(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id, @Valid @RequestBody RequestInfoRequest request) {
        return claimService.requestInformation(currentUser.require(userId, Role.STAFF), id, request.question());
    }

    /** Assigned officer approves an amount (above 0, up to coverage limit): UNDER_REVIEW -> APPROVED. */
    @PostMapping("/api/claims/{id}/approve")
    public ClaimDetailResponse approve(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
        return claimService.approve(currentUser.require(userId, Role.STAFF), id, request.amount());
    }

    /** Assigned officer rejects with a reason: UNDER_REVIEW -> REJECTED. */
    @PostMapping("/api/claims/{id}/reject")
    public ClaimDetailResponse reject(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return claimService.reject(currentUser.require(userId, Role.STAFF), id, request.reason());
    }

    /** Assigned officer confirms payment of the approved amount: APPROVED -> SETTLED. */
    @PostMapping("/api/claims/{id}/settle")
    public ClaimDetailResponse settle(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id) {
        return claimService.settle(currentUser.require(userId, Role.STAFF), id);
    }
}
