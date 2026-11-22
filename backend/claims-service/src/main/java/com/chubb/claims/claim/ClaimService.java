package com.chubb.claims.claim;

import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.Role;
import com.chubb.claims.web.ApiException;
import com.chubb.claims.web.FieldValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Claim use cases. Controllers handle HTTP and identity; the rules that need the database live here. */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final PolicyRepository policies;

    public ClaimService(ClaimRepository claims, PolicyRepository policies) {
        this.claims = claims;
        this.policies = policies;
    }

    /**
     * Reports an incident: creates a claim in SUBMITTED status.
     * Field checks (required, not in future, lengths) have already run on the request.
     */
    @Transactional
    public ClaimSummaryResponse submit(AppUser claimant, SubmitClaimRequest request) {
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
        return ClaimSummaryResponse.from(claims.saveAndFlush(claim));
    }

    /**
     * One claim in full. Staff can see any claim; a claimant only their own.
     * Someone else's claim gets the same 404 as a missing one, so claim ids can't be probed.
     */
    @Transactional(readOnly = true)
    public ClaimDetailResponse detail(AppUser caller, UUID claimId) {
        return claims.findDetailedById(claimId)
                .filter(c -> caller.getRole() == Role.STAFF || c.getClaimant().getId().equals(caller.getId()))
                .map(ClaimDetailResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    /** The claimant's own claims, newest first. */
    @Transactional(readOnly = true)
    public List<ClaimSummaryResponse> claimsOf(AppUser claimant) {
        return claims.findByClaimantIdOrderBySubmittedAtDesc(claimant.getId()).stream()
                .map(ClaimSummaryResponse::from)
                .toList();
    }

    /** All claims, newest first, for staff. */
    @Transactional(readOnly = true)
    public List<ClaimSummaryResponse> allClaims() {
        return claims.findAllByOrderBySubmittedAtDesc().stream()
                .map(ClaimSummaryResponse::from)
                .toList();
    }
}
