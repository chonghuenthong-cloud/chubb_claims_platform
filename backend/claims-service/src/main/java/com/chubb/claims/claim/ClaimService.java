package com.chubb.claims.claim;

import com.chubb.claims.events.OutboxWriter;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.user.AppUser;
import com.chubb.claims.web.ApiException;
import com.chubb.claims.web.FieldValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Claim use cases. Controllers handle HTTP and identity; the rules that need the database live here. */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final OutboxWriter outbox;

    public ClaimService(ClaimRepository claims, PolicyRepository policies, OutboxWriter outbox) {
        this.claims = claims;
        this.policies = policies;
        this.outbox = outbox;
    }

    /**
     * Reports an incident: creates a claim in SUBMITTED status.
     * Field checks (required, not in future, lengths) have already run on the request.
     */
    @Transactional
    public ClaimantClaimSummary submit(AppUser claimant, SubmitClaimRequest request) {
        // Another person's policy gets the same answer as a missing one, so policy ids can't be probed
        Policy policy = policies.findById(request.policyId())
                .filter(p -> p.getHolder().getId().equals(claimant.getId()))
                .orElseThrow(() -> new FieldValidationException("policyId", "Policy not found"));

        if (!policy.covers(request.incidentDate())) {
            throw new FieldValidationException("incidentDate",
                    "Incident date must be within the policy period (" + policy.getStartDate()
                            + " to " + policy.getEndDate() + ")");
        }

        String claimNumber = "CLM-%06d".formatted(claims.nextClaimNumber());
        Claim claim = Claim.submit(claimNumber, policy, claimant,
                request.incidentDate(), request.incidentDescription().trim(), request.estimatedLoss());

        // Flush now so database-generated timestamps are set before the response is built
        // saveAndFlush is right here: the claim is new, so it is persisted (not merged)
        Claim saved = claims.saveAndFlush(claim);
        outbox.store(saved);   // CLAIM_SUBMITTED, in this same transaction
        return ClaimantClaimSummary.from(saved);
    }

    /** Staff: any claim in full, including internal figures. */
    @Transactional(readOnly = true)
    public ClaimDetailResponse staffDetail(UUID claimId) {
        return ClaimDetailResponse.from(load(claimId));
    }

    /**
     * Claimant: one of their own claims, without internal figures.
     * Someone else's claim gets the same 404 as a missing one, so claim ids can't be probed.
     */
    @Transactional(readOnly = true)
    public ClaimantClaimDetail claimantDetail(AppUser claimant, UUID claimId) {
        return ClaimantClaimDetail.from(loadOwn(claimant, claimId));
    }

    /** The claimant's own claims, newest first, without internal figures. */
    @Transactional(readOnly = true)
    public List<ClaimantClaimSummary> claimsOf(AppUser claimant) {
        return claims.findByClaimantIdOrderBySubmittedAtDesc(claimant.getId()).stream()
                .map(ClaimantClaimSummary::from)
                .toList();
    }

    /** Staff work queue filters. */
    public enum QueueFilter { ALL, UNASSIGNED, MINE }

    /** Claims for the staff work queue, newest first. */
    @Transactional(readOnly = true)
    public List<ClaimSummaryResponse> queue(AppUser staff, QueueFilter filter) {
        List<Claim> result = switch (filter) {
            case ALL -> claims.findAllByOrderBySubmittedAtDesc();
            case UNASSIGNED -> claims.findByAssignedOfficerIsNullOrderBySubmittedAtDesc();
            case MINE -> claims.findByAssignedOfficerIdOrderBySubmittedAtDesc(staff.getId());
        };
        return result.stream().map(ClaimSummaryResponse::from).toList();
    }

    // --- Actions. Each loads the claim, applies the domain rule, saves, and returns the updated claim. ---
    // Flushing inside the transaction makes the version check run here: if another officer changed the claim
    // at the same moment, this fails with a conflict instead of silently overwriting their change.

    @Transactional
    public ClaimDetailResponse pickUp(AppUser officer, UUID claimId) {
        Claim claim = load(claimId);
        claim.pickUp(officer);
        return save(claim);
    }

    @Transactional
    public ClaimDetailResponse reviseReserve(AppUser officer, UUID claimId, BigDecimal amount) {
        Claim claim = load(claimId);
        claim.reviseReserve(officer, amount);
        return save(claim);
    }

    @Transactional
    public ClaimDetailResponse requestInformation(AppUser officer, UUID claimId, String question) {
        Claim claim = load(claimId);
        claim.requestInformation(officer, question.trim());
        return save(claim);
    }

    /** Claimant answers the open question. Someone else's claim gets 404, as with viewing. Returns the claimant view. */
    @Transactional
    public ClaimantClaimDetail answerInformationRequest(AppUser claimant, UUID claimId, String response) {
        Claim claim = loadOwn(claimant, claimId);
        claim.answerInformationRequest(claimant, response.trim());
        claims.flush();
        outbox.store(claim);
        return ClaimantClaimDetail.from(claim);
    }

    @Transactional
    public ClaimDetailResponse approve(AppUser officer, UUID claimId, BigDecimal amount) {
        Claim claim = load(claimId);
        claim.approve(officer, amount);
        return save(claim);
    }

    @Transactional
    public ClaimDetailResponse reject(AppUser officer, UUID claimId, String reason) {
        Claim claim = load(claimId);
        claim.reject(officer, reason.trim());
        return save(claim);
    }

    @Transactional
    public ClaimDetailResponse settle(AppUser officer, UUID claimId) {
        Claim claim = load(claimId);
        claim.settle(officer);
        return save(claim);
    }

    private Claim load(UUID claimId) {
        return claims.findDetailedById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    /** The claimant's own claim; someone else's is reported as not found. */
    private Claim loadOwn(AppUser claimant, UUID claimId) {
        return claims.findDetailedById(claimId)
                .filter(c -> c.getClaimant().getId().equals(claimant.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    /**
     * The claim is already managed in this transaction, so flushing writes the changes (and cascades new history
     * and information requests). Calling save() here would "merge" instead, which copies new child rows wrongly.
     */
    private ClaimDetailResponse save(Claim claim) {
        claims.flush();
        outbox.store(claim);   // the action's events, in this same transaction
        return ClaimDetailResponse.from(claim);
    }
}
